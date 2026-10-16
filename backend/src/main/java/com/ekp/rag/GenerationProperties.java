package com.ekp.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RAG generation settings ({@code ekp.generation.*}).
 *
 * @param enabled          master switch; also off when apiKey is blank
 * @param baseUrl          OpenAI-compatible API base, e.g. https://api.openai.com/v1
 * @param apiKey           provider API key (shares OPENAI_API_KEY with embeddings)
 * @param model            chat model, e.g. gpt-4o-mini
 * @param maxOutputTokens  cap on generated tokens per answer
 * @param temperature      sampling temperature; low values keep answers grounded
 * @param connectTimeout   TCP connect timeout (no read timeout: streaming is long-lived)
 * @param maxRetries       retries when the stream fails to *establish* (429/5xx)
 * @param initialBackoff   first retry delay (doubles with jitter)
 * @param maxBackoff       backoff ceiling
 * @param retrievalK       chunks retrieved to build the context
 * @param maxContextChars  character budget for the assembled context
 */
@ConfigurationProperties(prefix = "ekp.generation")
public record GenerationProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int maxOutputTokens,
        double temperature,
        Duration connectTimeout,
        int maxRetries,
        Duration initialBackoff,
        Duration maxBackoff,
        int retrievalK,
        int maxContextChars
) {
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
