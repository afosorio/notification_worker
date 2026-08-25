package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.SubscriptionRepository;
import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;
import com.cobre.notifications.domain.Subscription;
import com.cobre.notifications.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessNotificationEventServiceTest {

    private final NotificationEventRepository events = mock(NotificationEventRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final WebhookClient webhook = mock(WebhookClient.class);
    private final ProcessNotificationEventService service =
            new ProcessNotificationEventService(events, subscriptions, webhook);

    @Test
    void marksEventCompletedAfterSuccessfulDelivery() {
        var event = pendingEvent();
        when(events.findById(event.eventId())).thenReturn(Optional.of(event));
        when(subscriptions.findByClientId(event.clientId())).thenReturn(Optional.of(activeSubscription()));
        when(webhook.deliver(event)).thenReturn(WebhookClient.DeliveryResult.success());

        service.execute(event.eventId());

        verify(events).save(any(NotificationEvent.class));
        var saved = org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(saved.capture());
        assertEquals(DeliveryStatus.COMPLETED, saved.getValue().deliveryStatus());
    }

    private NotificationEvent pendingEvent() {
        var now = Instant.parse("2024-03-15T11:20:18Z");
        return new NotificationEvent("EVT003", "CLIENT002", "credit_transfer", "transfer", now,
                null, DeliveryStatus.PENDING, 0, null, null, now, now);
    }

    private Subscription activeSubscription() {
        var now = Instant.parse("2024-03-15T11:20:18Z");
        return new Subscription(1L, "CLIENT002", SubscriptionStatus.ACTIVE, now, now);
    }
}
