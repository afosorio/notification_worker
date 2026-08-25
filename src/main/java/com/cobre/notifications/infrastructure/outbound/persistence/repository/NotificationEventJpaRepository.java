package com.cobre.notifications.infrastructure.outbound.persistence.repository;

import com.cobre.notifications.infrastructure.outbound.persistence.entity.NotificationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, String>,
        JpaSpecificationExecutor<NotificationEventEntity> {
}
