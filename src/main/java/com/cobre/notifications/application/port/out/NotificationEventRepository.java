package com.cobre.notifications.application.port.out;

import com.cobre.notifications.domain.NotificationEvent;

import java.time.Instant;
import java.util.Optional;

public interface NotificationEventRepository {

    Optional<NotificationEvent> findById(String eventId);

    Page<NotificationEvent> search(SearchCriteria criteria);

    boolean replayIfFailed(String eventId);

    boolean claimPending(String eventId);

    NotificationEvent save(NotificationEvent event);

    record SearchCriteria(
            String clientId,
            String deliveryStatus,
            Instant from,
            Instant to,
            int page,
            int pageSize
    ) {
    }

    record Page<T>(
            java.util.List<T> items,
            int page,
            int pageSize,
            long totalItems
    ) {
    }
}
