package com.cobre.notifications.domain;

import java.time.Instant;

public record Subscription(
        long subscriptionId,
        String clientId,
        SubscriptionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }
}
