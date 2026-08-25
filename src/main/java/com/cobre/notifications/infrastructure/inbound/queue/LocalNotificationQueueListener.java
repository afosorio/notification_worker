package com.cobre.notifications.infrastructure.inbound.queue;

import com.cobre.notifications.application.port.in.ProcessNotificationEvent;
import com.cobre.notifications.infrastructure.outbound.queue.LocalNotificationQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LocalNotificationQueueListener {

    private final LocalNotificationQueue queue;
    private final ProcessNotificationEvent processNotificationEvent;

    public LocalNotificationQueueListener(LocalNotificationQueue queue,
                                          ProcessNotificationEvent processNotificationEvent) {
        this.queue = queue;
        this.processNotificationEvent = processNotificationEvent;
    }

    @Scheduled(fixedDelayString = "${notification.queue.poll-delay-ms:1000}")
    public void poll() throws InterruptedException {
        var eventId = queue.events().poll(100, TimeUnit.MILLISECONDS);
        if (eventId != null) {
            processNotificationEvent.execute(eventId);
        }
    }
}
