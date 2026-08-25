package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.GetNotificationEventDetail;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.NotificationEvent;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GetNotificationEventDetailService implements GetNotificationEventDetail {

    private final NotificationEventRepository repository;

    public GetNotificationEventDetailService(NotificationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<NotificationEvent> execute(String eventId) {
        return repository.findById(eventId);
    }
}
