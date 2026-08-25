package com.cobre.notifications.infrastructure.inbound.scheduler;

import com.cobre.notifications.application.port.in.ScheduleRetriesUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryScheduler {

    private final ScheduleRetriesUseCase scheduleRetries;
    private final int batchSize;

    public RetryScheduler(ScheduleRetriesUseCase scheduleRetries,
                          @org.springframework.beans.factory.annotation.Value("${notification.retry.scheduler-batch-size:50}") int batchSize) {
        this.scheduleRetries = scheduleRetries;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.retry.scheduler-delay-ms:1000}")
    public void run() {
        scheduleRetries.execute(batchSize);
    }
}
