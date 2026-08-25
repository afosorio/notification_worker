package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.ProcessNotificationEvent;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.SubscriptionRepository;
import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ProcessNotificationEventService implements ProcessNotificationEvent {

    private final NotificationEventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WebhookClient webhookClient;

    public ProcessNotificationEventService(NotificationEventRepository eventRepository,
                                           SubscriptionRepository subscriptionRepository,
                                           WebhookClient webhookClient) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookClient = webhookClient;
    }

    @Override
    @Transactional
    public void execute(String eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        if (event.deliveryStatus() != DeliveryStatus.PENDING) {
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

        var result = webhookClient.deliver(event);
        if (result.successful()) {
            eventRepository.save(withStatus(event, DeliveryStatus.COMPLETED, null, Instant.now()));
        } else {
            eventRepository.save(withStatus(event, DeliveryStatus.FAILED, result.errorMessage(), Instant.now()));
        }
    }

    private NotificationEvent withStatus(NotificationEvent event, DeliveryStatus status,
                                         String error, Instant deliveryDate) {
        return new NotificationEvent(event.eventId(), event.clientId(), event.eventType(), event.content(),
                event.eventCreatedAt(), deliveryDate, status, event.attemptCount() + 1,
                null, error, event.createdAt(), Instant.now());
    }
}
