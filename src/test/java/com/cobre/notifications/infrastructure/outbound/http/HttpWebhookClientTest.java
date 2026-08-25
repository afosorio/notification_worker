package com.cobre.notifications.infrastructure.outbound.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpWebhookClientTest {

    @Test
    void rejectsNonHttpsWebhook() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpWebhookClient("http://example.com/webhook", Duration.ofSeconds(1),
                        Duration.ofSeconds(1), 1));
    }

    @Test
    void rejectsLoopbackWebhook() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpWebhookClient("https://127.0.0.1/webhook", Duration.ofSeconds(1),
                        Duration.ofSeconds(1), 1));
    }
}
