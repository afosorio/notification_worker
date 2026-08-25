package com.cobre.notifications.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationEventTest {

    @Test
    void completesAnEventWithoutChangingItsAttemptCount() {
        var now = Instant.parse("2024-03-15T11:20:18Z");
        var event = new NotificationEvent("EVT1", "CLIENT1", "type", "content", now,
                null, DeliveryStatus.DELIVERING, 1, null, null, now, now);

        var completed = event.completed(now.plusSeconds(2));

        assertEquals(DeliveryStatus.COMPLETED, completed.deliveryStatus());
        assertEquals(1, completed.attemptCount());
        assertEquals(now.plusSeconds(2), completed.deliveryDate());
        assertEquals(null, completed.nextRetryAt());
    }

    @Test
    void retryPolicyHonorsRetryAfterAndAttemptLimit() {
        var now = Instant.parse("2024-03-15T11:20:18Z");
        var retryAfter = now.plusSeconds(30);
        var policy = new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertEquals(retryAfter, policy.nextRetryAt(1, retryAfter, now).orElseThrow());
        assertEquals(false, policy.canRetry(3, true));
        assertEquals(false, policy.canRetry(1, false));
    }

    @Test
    void rejectsInvalidRetryConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(3, Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(1)));
    }
}
