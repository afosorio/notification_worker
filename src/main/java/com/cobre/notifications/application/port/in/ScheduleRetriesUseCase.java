package com.cobre.notifications.application.port.in;

public interface ScheduleRetriesUseCase {

    int execute(int batchSize);
}
