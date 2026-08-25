package com.cobre.notifications.infrastructure.inbound.scheduler;

import com.cobre.notifications.application.port.in.RecoverAbandonedDeliveriesUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecoveryScheduler {

    private final RecoverAbandonedDeliveriesUseCase recoverAbandonedDeliveries;
    private final int batchSize;

    public RecoveryScheduler(RecoverAbandonedDeliveriesUseCase recoverAbandonedDeliveries,
                             @org.springframework.beans.factory.annotation.Value("${notification.recovery.batch-size:50}") int batchSize) {
        this.recoverAbandonedDeliveries = recoverAbandonedDeliveries;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.recovery.scheduler-delay-ms:10000}")
    public void run() {
        recoverAbandonedDeliveries.execute(batchSize);
    }
}
