package com.cobre.notifications.infrastructure.outbound.persistence;

import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;
import com.cobre.notifications.infrastructure.outbound.persistence.entity.NotificationEventEntity;
import com.cobre.notifications.infrastructure.outbound.persistence.repository.NotificationEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;

@Repository
public class NotificationEventRepositoryAdapter implements NotificationEventRepository {

    private final NotificationEventJpaRepository repository;

    public NotificationEventRepositoryAdapter(NotificationEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationEvent> findById(String eventId) {
        return repository.findById(eventId).map(NotificationEventEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationEvent> search(SearchCriteria criteria) {
        var pageable = PageRequest.of(criteria.page() - 1, criteria.pageSize(),
                Sort.by(Sort.Order.desc("eventCreatedAt"), Sort.Order.desc("eventId")));
        var result = repository.findAll(specification(criteria), pageable);
        return new Page<>(result.getContent().stream().map(NotificationEventEntity::toDomain).toList(),
                criteria.page(), criteria.pageSize(), result.getTotalElements());
    }

    @Override
    @Transactional
    public boolean replayIfFailed(String eventId) {
        return repository.replayIfFailed(eventId) == 1;
    }

    @Override
    @Transactional
    public boolean claimPending(String eventId) {
        return repository.claimPending(eventId) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationEvent> findRetryable(Instant now, int limit) {
        return repository.findByDeliveryStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAscUpdatedAtAsc(
                        DeliveryStatus.RETRY_SCHEDULED, now, PageRequest.of(0, limit))
                .stream().map(NotificationEventEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean moveRetryScheduledToPending(String eventId) {
        return repository.moveRetryScheduledToPending(eventId) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationEvent> findAbandoned(DeliveryStatus status, Instant updatedBefore, int limit) {
        return repository.findByDeliveryStatusAndUpdatedAtLessThanOrderByUpdatedAtAsc(
                        status, updatedBefore, PageRequest.of(0, limit))
                .stream().map(NotificationEventEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean recoverIfStale(String eventId, DeliveryStatus expectedStatus, Instant updatedBefore) {
        return repository.recoverIfStale(eventId, expectedStatus, updatedBefore) == 1;
    }


    @Override
    public NotificationEvent save(NotificationEvent event) {
        return repository.save(NotificationEventEntity.fromDomain(event)).toDomain();
    }

    private Specification<NotificationEventEntity> specification(SearchCriteria criteria) {
        return (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("clientId"), criteria.clientId()));
            if (criteria.deliveryStatus() != null && !criteria.deliveryStatus().isBlank()) {
                predicates.add(builder.equal(root.get("deliveryStatus"),
                        DeliveryStatus.valueOf(criteria.deliveryStatus().toUpperCase())));
            }
            if (criteria.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("eventCreatedAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("eventCreatedAt"), criteria.to()));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
