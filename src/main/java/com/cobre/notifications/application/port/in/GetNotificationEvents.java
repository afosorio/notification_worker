package com.cobre.notifications.application.port.in;

import com.cobre.notifications.application.port.out.NotificationEventRepository;

public interface GetNotificationEvents {

    NotificationEventRepository.Page<?> execute(NotificationEventRepository.SearchCriteria criteria);
}
