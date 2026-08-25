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
}
