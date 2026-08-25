package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.GetNotificationEvents;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.NotificationEvent;
import org.springframework.stereotype.Service;

@Service
public class GetNotificationEventsService implements GetNotificationEvents {

    private final NotificationEventRepository repository;

    public GetNotificationEventsService(NotificationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationEventRepository.Page<NotificationEvent> execute(NotificationEventRepository.SearchCriteria criteria) {
        return repository.search(criteria);
    }
}
