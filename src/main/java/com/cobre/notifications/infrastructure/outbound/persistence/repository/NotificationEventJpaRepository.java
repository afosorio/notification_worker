package com.cobre.notifications.infrastructure.outbound.persistence.repository;

import com.cobre.notifications.infrastructure.outbound.persistence.entity.NotificationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

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

    List<NotificationEventEntity> findByDeliveryStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAscUpdatedAtAsc(
            com.cobre.notifications.domain.DeliveryStatus deliveryStatus, Instant now, Pageable pageable);

    @Modifying
    @Query("update NotificationEventEntity e "
            + "set e.deliveryStatus = com.cobre.notifications.domain.DeliveryStatus.PENDING, "
            + "e.nextRetryAt = null, e.updatedAt = current_timestamp "
            + "where e.eventId = :eventId "
            + "and e.deliveryStatus = com.cobre.notifications.domain.DeliveryStatus.RETRY_SCHEDULED "
            + "and e.nextRetryAt <= current_timestamp")
    int moveRetryScheduledToPending(@Param("eventId") String eventId);

    List<NotificationEventEntity> findByDeliveryStatusAndUpdatedAtLessThanOrderByUpdatedAtAsc(
            com.cobre.notifications.domain.DeliveryStatus deliveryStatus, Instant updatedBefore, Pageable pageable);

    @Modifying
    @Query("update NotificationEventEntity e "
            + "set e.deliveryStatus = com.cobre.notifications.domain.DeliveryStatus.PENDING, "
            + "e.nextRetryAt = null, e.updatedAt = current_timestamp "
            + "where e.eventId = :eventId "
            + "and e.deliveryStatus = :expectedStatus "
            + "and e.updatedAt < :updatedBefore")
    int recoverIfStale(@Param("eventId") String eventId,
                       @Param("expectedStatus") com.cobre.notifications.domain.DeliveryStatus expectedStatus,
                       @Param("updatedBefore") Instant updatedBefore);
}
