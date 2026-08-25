package com.cobre.notifications.domain;

import java.time.Instant;

public record Subscription(
        String subscriptionId,
        String clientId,
        SubscriptionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }
}
