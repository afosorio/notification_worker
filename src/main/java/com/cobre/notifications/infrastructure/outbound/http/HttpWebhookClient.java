package com.cobre.notifications.infrastructure.outbound.http;

import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.NotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

@Component
public class HttpWebhookClient implements WebhookClient {

    private final RestClient restClient;
    private final URI webhookUri;

    public HttpWebhookClient(@Value("${notification.webhook.url}") String webhookUrl) {
        this.webhookUri = URI.create(webhookUrl);
        if (!"https".equalsIgnoreCase(webhookUri.getScheme())) {
            throw new IllegalArgumentException("Webhook URL must use HTTPS");
        }
        this.restClient = RestClient.builder().build();
    }

    @Override
    public DeliveryResult deliver(NotificationEvent event) {
        try {
            restClient.post()
                    .uri(webhookUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new WebhookPayload(event.eventId(), event.clientId(), event.eventType(), event.content()))
                    .retrieve()
                    .toBodilessEntity();
            return DeliveryResult.success();
        } catch (RestClientException | IllegalArgumentException exception) {
            return DeliveryResult.failure(exception.getMessage());
        }
    }

    private record WebhookPayload(String eventId, String clientId, String eventType, String content) {
    }
}
