package com.cobre.notifications.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Business policy for deciding when a retryable delivery may run again. */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;

    public RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be positive");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must not be less than initialDelay");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    public boolean canRetry(int attemptCount, boolean retryable) {
        return retryable && attemptCount < maxAttempts;
    }

    public Optional<Instant> nextRetryAt(int attemptCount, Instant retryAfter, Instant now) {
        if (retryAfter != null && retryAfter.isAfter(now)) {
            return Optional.of(retryAfter);
        }

        long initialSeconds = Math.max(1, initialDelay.toSeconds());
        long maxSeconds = Math.max(initialSeconds, maxDelay.toSeconds());
        int exponent = Math.min(Math.max(0, attemptCount - 1), 30);
        long exponentialSeconds = Math.min(maxSeconds, saturatingMultiply(initialSeconds, 1L << exponent));
        long jitter = Math.max(1, exponentialSeconds / 4);
        long adjustedSeconds = Math.max(0, exponentialSeconds
                + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1));
        return Optional.of(now.plusSeconds(adjustedSeconds));
    }

    private long saturatingMultiply(long left, long right) {
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }
}
