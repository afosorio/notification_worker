package com.cobre.notifications.infrastructure.outbound.persistence.repository;

import com.cobre.notifications.infrastructure.outbound.persistence.entity.NotificationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, String>,
        JpaSpecificationExecutor<NotificationEventEntity> {

    @Modifying
    @Query("update NotificationEventEntity e "
            + "set e.deliveryStatus = com.cobre.notifications.domain.DeliveryStatus.PENDING, "
            + "e.attemptCount = 0, e.nextRetryAt = null, e.lastError = null, "
            + "e.updatedAt = current_timestamp "
            + "where e.eventId = :eventId "
            + "and e.deliveryStatus = com.cobre.notifications.domain.DeliveryStatus.FAILED")
    int replayIfFailed(@Param("eventId") String eventId);

    @Modifying
    @Query("update NotificationEventEntity e "
            + "set e.deliveryStatus = com.cobre.notifications.domain.DeliveryStatus.DELIVERING, "
            + "e.attemptCount = e.attemptCount + 1, e.updatedAt = current_timestamp "
            + "where e.eventId = :eventId "
            + "and e.deliveryStatus = com.cobre.notifications.domain.DeliveryStatus.PENDING")
    int claimPending(@Param("eventId") String eventId);
}
