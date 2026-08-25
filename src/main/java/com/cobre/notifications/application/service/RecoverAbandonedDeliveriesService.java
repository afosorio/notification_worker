package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.in.RecoverAbandonedDeliveriesUseCase;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.NotificationQueue;
import com.cobre.notifications.domain.DeliveryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class RecoverAbandonedDeliveriesService implements RecoverAbandonedDeliveriesUseCase {

    private final NotificationEventRepository repository;
    private final NotificationQueue queue;
    private final Duration timeout;

    public RecoverAbandonedDeliveriesService(NotificationEventRepository repository,
                                             NotificationQueue queue,
                                             @org.springframework.beans.factory.annotation.Value("${notification.recovery.timeout:PT5M}") Duration timeout) {
        this.repository = repository;
        this.queue = queue;
        this.timeout = timeout;
    }

    @Override
    @Transactional
    public int execute(int batchSize) {
        var cutoff = Instant.now().minus(timeout);
        int recovered = recover(DeliveryStatus.PENDING, cutoff, batchSize);
        recovered += recover(DeliveryStatus.DELIVERING, cutoff, batchSize);
        return recovered;
    }

    private int recover(DeliveryStatus status, Instant cutoff, int batchSize) {
        int recovered = 0;
        for (var event : repository.findAbandoned(status, cutoff, batchSize)) {
            if (repository.recoverIfStale(event.eventId(), status, cutoff)) {
                queue.publish(event.eventId());
                recovered++;
            }
        }
        return recovered;
    }
}
