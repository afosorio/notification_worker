package com.cobre.notifications.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class DeliveryMetrics {

    private final Counter completed;
    private final Counter failed;
    private final Counter retryScheduled;
    private final Timer webhookLatency;

    public DeliveryMetrics(MeterRegistry registry) {
        this.completed = registry.counter("notification.delivery.completed");
        this.failed = registry.counter("notification.delivery.failed");
        this.retryScheduled = registry.counter("notification.delivery.retry_scheduled");
        this.webhookLatency = registry.timer("notification.webhook.latency");
    }

    public void recordCompleted() { completed.increment(); }

    public void recordFailed() { failed.increment(); }

    public void recordRetryScheduled() { retryScheduled.increment(); }

    public void recordWebhookLatency(Duration duration) { webhookLatency.record(duration); }
}
