package com.cobre.notifications.domain;

public enum DeliveryStatus {
    PENDING,
    DELIVERING,
    COMPLETED,
    RETRY_SCHEDULED,
    FAILED,
    NOT_SUBSCRIBED,
    SUBSCRIPTION_INACTIVE
}
