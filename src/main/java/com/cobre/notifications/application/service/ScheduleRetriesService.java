package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.ScheduleRetriesUseCase;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.NotificationQueue;
import com.cobre.notifications.infrastructure.config.DeliveryMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ScheduleRetriesService implements ScheduleRetriesUseCase {

    private final NotificationEventRepository repository;
    private final NotificationQueue queue;
    private final DeliveryMetrics metrics;

    public ScheduleRetriesService(NotificationEventRepository repository, NotificationQueue queue,
                                  DeliveryMetrics metrics) {
        this.repository = repository;
        this.queue = queue;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public int execute(int batchSize) {
        int published = 0;
        for (var event : repository.findRetryable(Instant.now(), batchSize)) {
            if (repository.moveRetryScheduledToPending(event.eventId())) {
                queue.publish(event.eventId());
                metrics.recordRetryActivated();
                published++;
            }
        }
        return published;
    }
}
