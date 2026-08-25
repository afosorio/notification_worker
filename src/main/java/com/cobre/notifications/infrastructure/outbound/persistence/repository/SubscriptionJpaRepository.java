package com.cobre.notifications.infrastructure.outbound.persistence.repository;

import com.cobre.notifications.infrastructure.outbound.persistence.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, Long> {

    Optional<SubscriptionEntity> findByClientId(String clientId);
}
