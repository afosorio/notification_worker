package com.cobre.notifications.infrastructure.outbound.http;

import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.NotificationEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

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
            return restClient.post()
                    .uri(webhookUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new WebhookPayload(event.eventId(), event.eventType(), event.content(), event.clientId()))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status >= 200 && status < 300) {
                            return DeliveryResult.success();
                        }
                        boolean retryable = status == 408 || status == 429 || status == 500
                                || status == 502 || status == 503 || status == 504;
                        return DeliveryResult.failure(retryable, "Webhook returned HTTP " + status,
                                status == 429 ? parseRetryAfter(response.getHeaders().getFirst("Retry-After")) : null);
                    });
        } catch (RestClientException | IllegalArgumentException exception) {
            return DeliveryResult.failure(true, sanitize(exception.getMessage()), null);
        }
    }

    private Instant parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.now().plusSeconds(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                return ZonedDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignoredDate) {
                return null;
            }
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Webhook delivery failed";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private record WebhookPayload(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            String content,
            @JsonProperty("client_id") String clientId) {
    }
}
