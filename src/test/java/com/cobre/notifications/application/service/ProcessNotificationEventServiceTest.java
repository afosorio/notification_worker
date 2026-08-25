package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.SubscriptionRepository;
import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;
import com.cobre.notifications.domain.RetryPolicy;
import com.cobre.notifications.domain.Subscription;
import com.cobre.notifications.domain.SubscriptionStatus;
import com.cobre.notifications.infrastructure.config.DeliveryMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessNotificationEventServiceTest {

    private final NotificationEventRepository events = mock(NotificationEventRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final WebhookClient webhook = mock(WebhookClient.class);
    private final DeliveryMetrics metrics = new DeliveryMetrics(new SimpleMeterRegistry());
    private final ProcessNotificationEventService service =
            new ProcessNotificationEventService(events, subscriptions, webhook,
                    new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(1)), metrics);

    @Test
    void marksEventCompletedAfterSuccessfulDelivery() {
        var event = pendingEvent();
        when(events.findById(event.eventId())).thenReturn(Optional.of(event));
        when(events.claimPending(event.eventId())).thenReturn(true);
        when(subscriptions.findByClientId(event.clientId())).thenReturn(Optional.of(activeSubscription()));
        when(webhook.deliver(event)).thenReturn(WebhookClient.DeliveryResult.success());

        service.execute(event.eventId());

        verify(events).save(any(NotificationEvent.class));
        var saved = org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(saved.capture());
        assertEquals(DeliveryStatus.COMPLETED, saved.getValue().deliveryStatus());
    }

    @Test
    void concurrentProcessingInvokesWebhookOnlyForTheClaimWinner() throws Exception {
        var event = pendingEvent();
        var firstClaim = new AtomicBoolean(true);
        when(events.findById(event.eventId())).thenReturn(Optional.of(event));
        when(events.claimPending(event.eventId())).thenAnswer(invocation -> firstClaim.getAndSet(false));
        when(subscriptions.findByClientId(event.clientId())).thenReturn(Optional.of(activeSubscription()));
        when(webhook.deliver(event)).thenReturn(WebhookClient.DeliveryResult.success());

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var first = executor.submit(() -> { start.await(); service.execute(event.eventId()); return null; });
        var second = executor.submit(() -> { start.await(); service.execute(event.eventId()); return null; });
        start.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        verify(webhook, times(1)).deliver(event);
    }

    @Test
    void retryableFailureSchedulesAnotherAttempt() {
        var event = pendingEventWithAttempts(0);
        when(events.findById(event.eventId())).thenReturn(Optional.of(event));
        when(events.claimPending(event.eventId())).thenReturn(true);
        when(subscriptions.findByClientId(event.clientId())).thenReturn(Optional.of(activeSubscription()));
        when(webhook.deliver(event)).thenReturn(WebhookClient.DeliveryResult.failure(true, "timeout", null));

        service.execute(event.eventId());

        var saved = org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(saved.capture());
        assertEquals(DeliveryStatus.RETRY_SCHEDULED, saved.getValue().deliveryStatus());
    }

    @Test
    void retryableFailureAfterMaxAttemptsBecomesFailed() {
        var event = pendingEventWithAttempts(3);
        when(events.findById(event.eventId())).thenReturn(Optional.of(event));
        when(events.claimPending(event.eventId())).thenReturn(true);
        when(subscriptions.findByClientId(event.clientId())).thenReturn(Optional.of(activeSubscription()));
        when(webhook.deliver(event)).thenReturn(WebhookClient.DeliveryResult.failure(true, "timeout", null));

        service.execute(event.eventId());

        var saved = org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(saved.capture());
        assertEquals(DeliveryStatus.FAILED, saved.getValue().deliveryStatus());
    }

    private NotificationEvent pendingEvent() {
        return pendingEventWithAttempts(0);
    }

    private NotificationEvent pendingEventWithAttempts(int attempts) {
        var now = Instant.parse("2024-03-15T11:20:18Z");
        return new NotificationEvent("EVT003", "CLIENT002", "credit_transfer", "transfer", now,
                null, DeliveryStatus.PENDING, attempts, null, null, now, now);
    }

    private Subscription activeSubscription() {
        var now = Instant.parse("2024-03-15T11:20:18Z");
        return new Subscription("SUB-1", "CLIENT002", SubscriptionStatus.ACTIVE, now, now);
    }
}
