package com.cobre.notifications.infrastructure.outbound.persistence;

import com.cobre.notifications.application.port.out.SubscriptionRepository;
import com.cobre.notifications.domain.Subscription;
import com.cobre.notifications.infrastructure.outbound.persistence.repository.SubscriptionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository repository;

    public SubscriptionRepositoryAdapter(SubscriptionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Subscription> findByClientId(String clientId) {
        return repository.findByClientId(clientId).map(entity -> entity.toDomain());
    }
}
