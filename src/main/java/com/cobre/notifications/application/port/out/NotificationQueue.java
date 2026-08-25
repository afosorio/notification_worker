package com.cobre.notifications.application.port.out;

public interface NotificationQueue {

    void publish(String eventId);
}
