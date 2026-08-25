package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.out.NotificationEventRepository;
import com.cobre.notifications.application.port.out.NotificationQueue;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayNotificationServiceTest {

    private final NotificationEventRepository repository = mock(NotificationEventRepository.class);
    private final NotificationQueue queue = mock(NotificationQueue.class);
    private final ReplayNotificationService service = new ReplayNotificationService(repository, queue);

    @Test
    void publishesOnlyAfterTheConditionalUpdateSucceeds() {
        when(repository.findById("EVT003")).thenReturn(Optional.empty());
        when(repository.replayIfFailed("EVT003")).thenReturn(true);

        // A missing event must never be published, regardless of an erroneous update result.
        assertThrows(NotificationEventNotFoundException.class, () -> service.execute("EVT003"));
        verify(queue, never()).publish("EVT003");
    }

    @Test
    void rejectsAnEventThatIsNotFailed() {
        when(repository.findById("EVT001")).thenReturn(Optional.of(event("EVT001")));
        when(repository.replayIfFailed("EVT001")).thenReturn(false);

        assertThrows(NotificationEventNotReplayableException.class, () -> service.execute("EVT001"));
        verify(queue, never()).publish("EVT001");
    }

    @Test
    void publishesWhenFailedStateIsTransitionedAtomically() {
        when(repository.findById("EVT003")).thenReturn(Optional.of(event("EVT003")));
        when(repository.replayIfFailed("EVT003")).thenReturn(true);

        service.execute("EVT003");

        verify(queue).publish("EVT003");
    }

    private NotificationEvent event(String eventId) {
        var now = Instant.parse("2024-03-15T11:20:18Z");
        return new NotificationEvent(eventId, "CLIENT002", "credit_transfer", "transfer", now,
                null, DeliveryStatus.FAILED, 1, null, "timeout", now, now);
    }
}
