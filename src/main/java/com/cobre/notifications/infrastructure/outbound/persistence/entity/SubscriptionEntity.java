package com.cobre.notifications.infrastructure.outbound.persistence.entity;

import com.cobre.notifications.domain.Subscription;
import com.cobre.notifications.domain.SubscriptionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "subscription")
public class SubscriptionEntity {

    @Id
    private String subscriptionId;
    private String clientId;
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    protected SubscriptionEntity() {
    }

    public static SubscriptionEntity active(String subscriptionId, String clientId, Instant now) {
        var entity = new SubscriptionEntity();
        entity.subscriptionId = subscriptionId;
        entity.clientId = clientId;
        entity.status = SubscriptionStatus.ACTIVE;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Subscription toDomain() {
        return new Subscription(subscriptionId, clientId, status, createdAt, updatedAt);
    }
}
