package com.cobre.notifications.infrastructure.outbound.http;

import com.cobre.notifications.application.port.out.WebhookClient;
import com.cobre.notifications.domain.NotificationEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class HttpWebhookClient implements WebhookClient {

    private final RestClient restClient;
    private final URI webhookUri;
    private final Semaphore concurrencyLimit;

    public HttpWebhookClient(@Value("${notification.webhook.url}") String webhookUrl,
                             @Value("${notification.webhook.connect-timeout:PT2S}") Duration connectTimeout,
                             @Value("${notification.webhook.read-timeout:PT5S}") Duration readTimeout,
                             @Value("${notification.webhook.max-concurrent:16}") int maxConcurrent) {
        this.webhookUri = URI.create(webhookUrl);
        validateDestination(webhookUri);
        var httpClient = HttpClients.custom().disableRedirectHandling().build();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.concurrencyLimit = new Semaphore(maxConcurrent);
    }

    @Override
    public DeliveryResult deliver(NotificationEvent event) {
        try {
            if (!concurrencyLimit.tryAcquire(1, 100, TimeUnit.MILLISECONDS)) {
                return DeliveryResult.failure(true, "Webhook concurrency limit reached", null);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DeliveryResult.failure(true, "Webhook delivery interrupted", null);
        }
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
        } finally {
            concurrencyLimit.release();
        }
    }

    private void validateDestination(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Webhook URL must be an HTTPS URL without credentials");
        }
        try {
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.getHostAddress().startsWith("169.254.")) {
                    throw new IllegalArgumentException("Webhook URL resolves to a private or local address");
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Webhook host cannot be resolved", exception);
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
