package com.cobre.notifications.application.port.in;

import com.cobre.notifications.domain.NotificationEvent;

import java.util.Optional;

public interface GetNotificationEventDetail {

    Optional<NotificationEvent> execute(String eventId);
}
