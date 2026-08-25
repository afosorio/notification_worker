package com.cobre.notifications.application.port.in;

public interface RecoverAbandonedDeliveriesUseCase {

    int execute(int batchSize);
}
