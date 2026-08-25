package com.cobre.notifications.application.port.in;

import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.NotificationEvent;

public interface GetNotificationEvents {

    NotificationEventRepository.Page<NotificationEvent> execute(NotificationEventRepository.SearchCriteria criteria);
}
