package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.ProcessNotificationEvent;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.SubscriptionRepository;
import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;
import com.cobre.notifications.infrastructure.config.DeliveryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProcessNotificationEventService implements ProcessNotificationEvent {

    private final NotificationEventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WebhookClient webhookClient;
    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final DeliveryMetrics metrics;
    private final Logger logger = LoggerFactory.getLogger(ProcessNotificationEventService.class);

    public ProcessNotificationEventService(NotificationEventRepository eventRepository,
                                           SubscriptionRepository subscriptionRepository,
                                           WebhookClient webhookClient,
                                           @org.springframework.beans.factory.annotation.Value("${notification.retry.max-attempts:3}") int maxAttempts,
                                           @org.springframework.beans.factory.annotation.Value("${notification.retry.initial-delay:PT1S}") Duration initialDelay,
                                           @org.springframework.beans.factory.annotation.Value("${notification.retry.max-delay:PT1M}") Duration maxDelay,
                                           DeliveryMetrics metrics) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookClient = webhookClient;
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public void execute(String eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        if (!eventRepository.claimPending(eventId)) {
            return;
        }
        event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }

        var subscription = subscriptionRepository.findByClientId(event.clientId());
        if (subscription.isEmpty()) {
            eventRepository.save(withStatus(event, DeliveryStatus.NOT_SUBSCRIBED, null, null));
            return;
        }
        if (!subscription.get().isActive()) {
            eventRepository.save(withStatus(event, DeliveryStatus.SUBSCRIPTION_INACTIVE, null, null));
            return;
        }

        var deliveryStarted = Instant.now();
        var result = webhookClient.deliver(event);
        metrics.recordWebhookLatency(Duration.between(deliveryStarted, Instant.now()));
        if (result.successful()) {
            eventRepository.save(withStatus(event, DeliveryStatus.COMPLETED, null, Instant.now()));
            metrics.recordCompleted();
            logger.info("notification_delivery eventId={} clientId={} status=COMPLETED attemptCount={} traceId={}",
                    event.eventId(), event.clientId(), event.attemptCount(), org.slf4j.MDC.get("traceId"));
        } else if (result.retryable() && event.attemptCount() < maxAttempts) {
            eventRepository.save(withStatus(event, DeliveryStatus.RETRY_SCHEDULED, result.errorMessage(),
                    retryAt(event.attemptCount(), result.retryAfter())));
            metrics.recordRetryScheduled();
            logger.warn("notification_delivery eventId={} clientId={} status=RETRY_SCHEDULED attemptCount={} traceId={}",
                    event.eventId(), event.clientId(), event.attemptCount(), org.slf4j.MDC.get("traceId"));
        } else {
            eventRepository.save(withStatus(event, DeliveryStatus.FAILED, result.errorMessage(), Instant.now()));
            metrics.recordFailed();
            logger.warn("notification_delivery eventId={} clientId={} status=FAILED attemptCount={} traceId={}",
                    event.eventId(), event.clientId(), event.attemptCount(), org.slf4j.MDC.get("traceId"));
        }
    }

    private Instant retryAt(int attemptCount, Instant retryAfter) {
        if (retryAfter != null && retryAfter.isAfter(Instant.now())) {
            return retryAfter;
        }
        long exponentialSeconds = Math.min(maxDelay.toSeconds(),
                initialDelay.toSeconds() * (1L << Math.min(attemptCount - 1, 30)));
        long jitter = Math.max(1, exponentialSeconds / 4);
        long adjustedSeconds = Math.max(0, exponentialSeconds
                + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1));
        return Instant.now().plusSeconds(adjustedSeconds);
    }

    private NotificationEvent withStatus(NotificationEvent event, DeliveryStatus status,
                                         String error, Instant deliveryDate) {
        return new NotificationEvent(event.eventId(), event.clientId(), event.eventType(), event.content(),
                event.eventCreatedAt(), status == DeliveryStatus.RETRY_SCHEDULED ? event.deliveryDate() : deliveryDate,
                status, event.attemptCount(), status == DeliveryStatus.RETRY_SCHEDULED ? deliveryDate : null,
                error, event.createdAt(), Instant.now());
    }
}
