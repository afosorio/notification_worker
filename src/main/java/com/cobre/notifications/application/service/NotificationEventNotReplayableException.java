package com.cobre.notifications.application.service;

public class NotificationEventNotReplayableException extends RuntimeException {

    public NotificationEventNotReplayableException(String eventId) {
        super("Notification event is not in FAILED state: " + eventId);
    }
}
