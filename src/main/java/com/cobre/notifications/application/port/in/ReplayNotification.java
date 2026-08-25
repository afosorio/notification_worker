package com.cobre.notifications.application.port.in;

public interface ReplayNotification {

    void execute(String eventId);
}
