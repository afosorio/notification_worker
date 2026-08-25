package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.ScheduleRetriesUseCase;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.NotificationQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ScheduleRetriesService implements ScheduleRetriesUseCase {

    private final NotificationEventRepository repository;
    private final NotificationQueue queue;

    public ScheduleRetriesService(NotificationEventRepository repository, NotificationQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    @Override
    @Transactional
    public int execute(int batchSize) {
        int published = 0;
        for (var event : repository.findRetryable(Instant.now(), batchSize)) {
            if (repository.moveRetryScheduledToPending(event.eventId())) {
                queue.publish(event.eventId());
                published++;
            }
        }
        return published;
    }
}
