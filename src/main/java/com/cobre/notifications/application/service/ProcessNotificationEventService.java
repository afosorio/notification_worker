package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.ProcessNotificationEvent;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.SubscriptionRepository;
import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.NotificationEvent;
import com.cobre.notifications.domain.RetryPolicy;
import com.cobre.notifications.infrastructure.config.DeliveryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;

@Service
public class ProcessNotificationEventService implements ProcessNotificationEvent {

    private final NotificationEventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WebhookClient webhookClient;
    private final RetryPolicy retryPolicy;
    private final DeliveryMetrics metrics;
    private final Logger logger = LoggerFactory.getLogger(ProcessNotificationEventService.class);

    public ProcessNotificationEventService(NotificationEventRepository eventRepository,
                                           SubscriptionRepository subscriptionRepository,
                                           WebhookClient webhookClient,
                                           RetryPolicy retryPolicy,
                                           DeliveryMetrics metrics) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookClient = webhookClient;
        this.retryPolicy = retryPolicy;
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
            eventRepository.save(event.notSubscribed());
            return;
        }
        if (!subscription.get().isActive()) {
            eventRepository.save(event.subscriptionInactive());
            return;
        }

        var deliveryStarted = Instant.now();
        var result = webhookClient.deliver(event);
        metrics.recordWebhookLatency(Duration.between(deliveryStarted, Instant.now()));
        if (result.successful()) {
            eventRepository.save(event.completed(Instant.now()));
            metrics.recordCompleted();
            logger.info("notification_delivery eventId={} clientId={} status=COMPLETED attemptCount={} traceId={}",
                    event.eventId(), event.clientId(), event.attemptCount(), org.slf4j.MDC.get("traceId"));
        } else if (retryPolicy.canRetry(event.attemptCount(), result.retryable())) {
            var retryAt = retryPolicy.nextRetryAt(event.attemptCount(), result.retryAfter(), Instant.now())
                    .orElseThrow();
            eventRepository.save(event.scheduleRetry(retryAt, result.errorMessage()));
            metrics.recordRetryScheduled();
            logger.warn("notification_delivery eventId={} clientId={} status=RETRY_SCHEDULED attemptCount={} traceId={}",
                    event.eventId(), event.clientId(), event.attemptCount(), org.slf4j.MDC.get("traceId"));
        } else {
            eventRepository.save(event.failed(result.errorMessage(), Instant.now()));
            metrics.recordFailed();
            logger.warn("notification_delivery eventId={} clientId={} status=FAILED attemptCount={} traceId={}",
                    event.eventId(), event.clientId(), event.attemptCount(), org.slf4j.MDC.get("traceId"));
        }
    }

}
