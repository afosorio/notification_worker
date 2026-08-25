package com.cobre.notifications.domain;

import java.time.Instant;

public record NotificationEvent(
        String eventId,
        String clientId,
        String eventType,
        String content,
        Instant eventCreatedAt,
        Instant deliveryDate,
        DeliveryStatus deliveryStatus,
        int attemptCount,
        Instant nextRetryAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {

    public NotificationEvent completed(Instant deliveryDate) {
        return withStatus(DeliveryStatus.COMPLETED, null, deliveryDate, null, deliveryDate);
    }

    public NotificationEvent scheduleRetry(Instant nextRetryAt, String error) {
        return withStatus(DeliveryStatus.RETRY_SCHEDULED, error, this.deliveryDate,
                nextRetryAt, Instant.now());
    }

    public NotificationEvent failed(String error, Instant deliveryDate) {
        return withStatus(DeliveryStatus.FAILED, error, deliveryDate, null, deliveryDate);
    }

    public NotificationEvent notSubscribed() {
        return withStatus(DeliveryStatus.NOT_SUBSCRIBED, null, null, null, Instant.now());
    }

    public NotificationEvent subscriptionInactive() {
        return withStatus(DeliveryStatus.SUBSCRIPTION_INACTIVE, null, null, null, Instant.now());
    }

    private NotificationEvent withStatus(DeliveryStatus status, String error,
                                         Instant deliveryDate, Instant nextRetryAt,
                                         Instant updatedAt) {
        return new NotificationEvent(eventId, clientId, eventType, content, eventCreatedAt,
                deliveryDate, status, attemptCount, nextRetryAt, error, createdAt, updatedAt);
    }
}
