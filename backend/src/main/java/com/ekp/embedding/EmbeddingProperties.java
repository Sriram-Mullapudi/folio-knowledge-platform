package com.ekp.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Embedding pipeline settings ({@code ekp.embedding.*}).
 *
 * @param enabled            master switch; also treated as off when apiKey is blank
 * @param baseUrl            API base, e.g. https://api.openai.com/v1
 * @param apiKey             provider API key (from OPENAI_API_KEY)
 * @param model              embedding model, e.g. text-embedding-3-small
 * @param dimensions         output dimensions; MUST equal the vector() column size
 * @param batchSize          chunks sent per API request
 * @param requestsPerMinute  client-side rate cap to stay under provider limits
 * @param maxRetries         attempts on 429/5xx before giving up
 * @param initialBackoff     first retry delay (doubles each attempt, with jitter)
 * @param maxBackoff         ceiling for the backoff delay
 * @param connectTimeout     TCP connect timeout
 * @param readTimeout        response read timeout
 */
@ConfigurationProperties(prefix = "ekp.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int dimensions,
        int batchSize,
        int requestsPerMinute,
        int maxRetries,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration connectTimeout,
        Duration readTimeout
) {
    /** Embeddings are usable only when explicitly enabled and a key is present. */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
