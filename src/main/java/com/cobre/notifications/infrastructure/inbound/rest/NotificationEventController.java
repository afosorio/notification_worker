package com.cobre.notifications.infrastructure.inbound.rest;

import com.cobre.notifications.application.port.in.GetNotificationEventDetail;
import com.cobre.notifications.application.port.in.GetNotificationEvents;
import com.cobre.notifications.application.port.in.ReplayNotification;
import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.NotificationEvent;
import com.cobre.notifications.domain.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/notification_events")
public class NotificationEventController {

    private final GetNotificationEvents getNotificationEvents;
    private final GetNotificationEventDetail getNotificationEventDetail;
    private final ReplayNotification replayNotification;

    public NotificationEventController(GetNotificationEvents getNotificationEvents,
                                       GetNotificationEventDetail getNotificationEventDetail,
                                       ReplayNotification replayNotification) {
        this.getNotificationEvents = getNotificationEvents;
        this.getNotificationEventDetail = getNotificationEventDetail;
        this.replayNotification = replayNotification;
    }

    @GetMapping
    public EventListResponse list(
            @RequestParam String clientId,
            @RequestParam(required = false) String deliveryStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validate(clientId, from, to, page, pageSize);
        if (deliveryStatus != null) {
            try {
                var parsedStatus = DeliveryStatus.valueOf(deliveryStatus.toUpperCase());
                if (parsedStatus != DeliveryStatus.COMPLETED && parsedStatus != DeliveryStatus.FAILED) {
                    throw new InvalidRequestException("deliveryStatus is invalid");
                }
                deliveryStatus = parsedStatus.name();
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException("deliveryStatus is invalid");
            }
        }
        var result = getNotificationEvents.execute(new NotificationEventRepository.SearchCriteria(
                clientId, deliveryStatus, from, to, page, pageSize));
        return new EventListResponse(result.items().stream().map(EventResponse::from).toList(), result.page(), result.pageSize());
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> detail(@PathVariable String eventId) {
        return getNotificationEventDetail.execute(eventId)
                .map(EventResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{eventId}/replay")
    public ResponseEntity<Void> replay(@PathVariable String eventId) {
        replayNotification.execute(eventId);
        return ResponseEntity.accepted().build();
    }

    private void validate(String clientId, Instant from, Instant to, int page, int pageSize) {
        if (clientId == null || clientId.isBlank()) {
            throw new InvalidRequestException("clientId is required");
        }
        if (page < 1 || pageSize < 1 || pageSize > 20) {
            throw new InvalidRequestException("page must be >= 1 and pageSize must be between 1 and 20");
        }
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new InvalidRequestException("from must be before or equal to to");
            }
            if (from.atZone(ZoneOffset.UTC).plusMonths(1).toInstant().isBefore(to)) {
                throw new InvalidRequestException("date range cannot exceed one month");
            }
        }
    }

    public record EventListResponse(java.util.List<EventResponse> events, int page, int pageSize) {
    }

    public record EventResponse(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            String content,
            @JsonProperty("delivery_date") Instant deliveryDate,
            @JsonProperty("delivery_status") String deliveryStatus,
            @JsonProperty("client_id") String clientId) {

        static EventResponse from(NotificationEvent event) {
            return new EventResponse(event.eventId(), event.eventType(), event.content(), event.deliveryDate(),
                    event.deliveryStatus().name().toLowerCase(), event.clientId());
        }
    }
}
