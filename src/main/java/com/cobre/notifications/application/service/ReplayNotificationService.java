package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.ReplayNotification;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.NotificationQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayNotificationService implements ReplayNotification {

    private final NotificationEventRepository repository;
    private final NotificationQueue queue;

    public ReplayNotificationService(NotificationEventRepository repository, NotificationQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    @Override
    @Transactional
    public void execute(String eventId) {
        if (repository.findById(eventId).isEmpty()) {
            throw new NotificationEventNotFoundException(eventId);
        }
        if (!repository.replayIfFailed(eventId)) {
            throw new NotificationEventNotReplayableException(eventId);
        }
        queue.publish(eventId);
    }
}
