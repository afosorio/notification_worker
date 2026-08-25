package com.cobre.notifications.application.port.out;

import com.cobre.notifications.domain.NotificationEvent;

import java.time.Instant;

public interface WebhookClient {

    DeliveryResult deliver(NotificationEvent event);

    record DeliveryResult(boolean successful, boolean retryable, String errorMessage, Instant retryAfter) {
        public static DeliveryResult success() {
            return new DeliveryResult(true, false, null, null);
        }

        public static DeliveryResult failure(boolean retryable, String errorMessage, Instant retryAfter) {
            return new DeliveryResult(false, retryable, errorMessage, retryAfter);
        }
    }
}
