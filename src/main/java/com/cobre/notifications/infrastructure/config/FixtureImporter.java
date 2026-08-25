package com.cobre.notifications.infrastructure.config;

import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.infrastructure.outbound.persistence.entity.NotificationEventEntity;
import com.cobre.notifications.infrastructure.outbound.persistence.entity.SubscriptionEntity;
import com.cobre.notifications.infrastructure.outbound.persistence.repository.NotificationEventJpaRepository;
import com.cobre.notifications.infrastructure.outbound.persistence.repository.SubscriptionJpaRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

@Configuration
public class FixtureImporter {

    @Bean
    CommandLineRunner importFixture(ObjectMapper objectMapper,
                                    NotificationEventJpaRepository eventRepository,
                                    SubscriptionJpaRepository subscriptionRepository) {
        return args -> importIfEmpty(objectMapper, eventRepository, subscriptionRepository);
    }

    @Transactional
    void importIfEmpty(ObjectMapper objectMapper,
                       NotificationEventJpaRepository eventRepository,
                       SubscriptionJpaRepository subscriptionRepository) throws IOException {
        if (eventRepository.count() > 0) {
            return;
        }

        var fixture = objectMapper.readValue(new ClassPathResource("notification_events.json").getInputStream(), Fixture.class);
        var now = Instant.now();
        Arrays.stream(fixture.events()).forEach(event -> {
            var deliveryDate = Instant.parse(event.deliveryDate());
            eventRepository.save(NotificationEventEntity.fromDomain(
                    new com.cobre.notifications.domain.NotificationEvent(
                            event.eventId(), event.clientId(), event.eventType(), event.content(),
                            deliveryDate, deliveryDate,
                            DeliveryStatus.valueOf(event.deliveryStatus().toUpperCase()),
                            0, null, null, now, now)));
            if (subscriptionRepository.findByClientId(event.clientId()).isEmpty()) {
                subscriptionRepository.save(SubscriptionEntity.active(event.clientId(), now));
            }
        });
    }

    private record Fixture(Event[] events) {
    }

    private record Event(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            String content,
            @JsonProperty("delivery_date") String deliveryDate,
            @JsonProperty("delivery_status") String deliveryStatus,
            @JsonProperty("client_id") String clientId
    ) {
    }
}
