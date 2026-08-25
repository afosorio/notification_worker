package com.cobre.notifications.application.port.out;

import com.cobre.notifications.domain.NotificationEvent;

public interface WebhookClient {

    DeliveryResult deliver(NotificationEvent event);

    record DeliveryResult(boolean successful, String errorMessage) {
        public static DeliveryResult success() {
            return new DeliveryResult(true, null);
        }

        public static DeliveryResult failure(String errorMessage) {
            return new DeliveryResult(false, errorMessage);
        }
    }
}
