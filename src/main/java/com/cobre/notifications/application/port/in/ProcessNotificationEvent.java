package com.cobre.notifications.application.port.in;

public interface ProcessNotificationEvent {

    void execute(String eventId);
}
