package com.ekp.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible embeddings client.
 *
 * <p>Each call sends one batch of inputs and returns one vector per input in
 * input order. Transient failures (HTTP 429 and 5xx, connect/read timeouts) are
 * retried with exponential backoff + jitter, honouring {@code Retry-After} when
 * present. A client-side {@link EmbeddingRateLimiter} spaces requests so we stay
 * under the provider's rate limit in the first place.
 */
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    private final RestClient restClient;
    private final EmbeddingProperties props;
    private final EmbeddingRateLimiter rateLimiter;

    public OpenAiEmbeddingClient(RestClient restClient, EmbeddingProperties props,
                                 EmbeddingRateLimiter rateLimiter) {
        this.restClient = restClient;
        this.props = props;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "input", inputs,
                "dimensions", props.dimensions());

        int attempt = 0;
        while (true) {
            attempt++;
            throttle();
            try {
                EmbeddingResponse response = restClient.post()
                        .uri("/embeddings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(EmbeddingResponse.class);
                return toVectors(response, inputs.size());
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!isRetryable(status) || attempt > props.maxRetries()) {
                    throw new EmbeddingException(
                            "Embedding request failed with HTTP " + status, e);
                }
                sleep(retryDelay(attempt, e), status);
            } catch (ResourceAccessException e) {
                // Connect/read timeout or network blip.
                if (attempt > props.maxRetries()) {
                    throw new EmbeddingException("Embedding request failed (transport)", e);
                }
                sleep(Vectors.backoff(attempt, props.initialBackoff(), props.maxBackoff()), 0);
            }
        }
    }

    private void throttle() {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Interrupted while rate limiting", e);
        }
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    /** Prefer the server's Retry-After (seconds) over computed backoff when present. */
    private Duration retryDelay(int attempt, RestClientResponseException e) {
        Duration computed = Vectors.backoff(attempt, props.initialBackoff(), props.maxBackoff());
        String retryAfter = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                Duration fromHeader = Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
                return fromHeader.compareTo(computed) > 0 ? fromHeader : computed;
            } catch (NumberFormatException ignored) {
                // Non-numeric (HTTP-date) Retry-After: fall back to computed backoff.
            }
        }
        return computed;
    }

    private void sleep(Duration delay, int status) {
        log.warn("Embedding call retry: status={}, backing off {} ms", status, delay.toMillis());
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Interrupted during retry backoff", e);
        }
    }

    private List<float[]> toVectors(EmbeddingResponse response, int expected) {
        if (response == null || response.data() == null || response.data().size() != expected) {
            throw new EmbeddingException("Embedding response size mismatch (expected "
                    + expected + ")");
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(Datum::index))
                .map(Datum::embedding)
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<Datum> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Datum(int index, float[] embedding) {}
}
