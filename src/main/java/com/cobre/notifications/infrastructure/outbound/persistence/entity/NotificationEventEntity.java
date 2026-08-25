package com.cobre.notifications.infrastructure.outbound.persistence.entity;

import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification_event")
public class NotificationEventEntity {

    @Id
    private String eventId;
    private String clientId;
    private String eventType;
    private String content;
    private Instant eventCreatedAt;
    private Instant deliveryDate;
    @Enumerated(EnumType.STRING)
    private DeliveryStatus deliveryStatus;
    private int attemptCount;
    private Instant nextRetryAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    protected NotificationEventEntity() {
    }

    public static NotificationEventEntity fromDomain(NotificationEvent event) {
        var entity = new NotificationEventEntity();
        entity.eventId = event.eventId();
        entity.clientId = event.clientId();
        entity.eventType = event.eventType();
        entity.content = event.content();
        entity.eventCreatedAt = event.eventCreatedAt();
        entity.deliveryDate = event.deliveryDate();
        entity.deliveryStatus = event.deliveryStatus();
        entity.attemptCount = event.attemptCount();
        entity.nextRetryAt = event.nextRetryAt();
        entity.lastError = event.lastError();
        entity.createdAt = event.createdAt();
        entity.updatedAt = event.updatedAt();
        return entity;
    }

    public NotificationEvent toDomain() {
        return new NotificationEvent(eventId, clientId, eventType, content, eventCreatedAt,
                deliveryDate, deliveryStatus, attemptCount, nextRetryAt, lastError, createdAt, updatedAt);
    }

    public String getEventId() { return eventId; }
    public String getClientId() { return clientId; }
    public String getEventType() { return eventType; }
    public String getContent() { return content; }
    public Instant getEventCreatedAt() { return eventCreatedAt; }
    public Instant getDeliveryDate() { return deliveryDate; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
