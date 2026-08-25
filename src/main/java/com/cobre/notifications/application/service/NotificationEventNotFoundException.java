package com.cobre.notifications.application.service;

public class NotificationEventNotFoundException extends RuntimeException {

    public NotificationEventNotFoundException(String eventId) {
        super("Notification event not found: " + eventId);
    }
}
