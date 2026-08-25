package com.cobre.notifications.infrastructure.outbound.queue;

import com.cobre.notifications.application.port.out.NotificationQueue;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class LocalNotificationQueue implements NotificationQueue {

    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();

    @Override
    public void publish(String eventId) {
        events.add(eventId);
    }

    public BlockingQueue<String> events() {
        return events;
    }
}
