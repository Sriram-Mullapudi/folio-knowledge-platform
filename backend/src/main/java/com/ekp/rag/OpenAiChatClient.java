package com.ekp.rag;

import com.ekp.embedding.Vectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Streams chat completions from an OpenAI-compatible API over Server-Sent
 * Events. Uses the JDK HTTP client because {@code BodyHandlers.ofLines()} gives
 * a lazy line stream — exactly what SSE needs — with no extra dependency and no
 * read timeout to truncate a long answer.
 *
 * <p>Retries apply only while <em>establishing</em> the stream (HTTP 429/5xx or
 * a transport error before any token arrives); once content is flowing we never
 * retry, since that would duplicate partial output.
 */
public class OpenAiChatClient implements ChatModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final int MAX_ERROR_CHARS = 500;

    private final HttpClient http;
    private final GenerationProperties props;
    private final ObjectMapper mapper;

    public OpenAiChatClient(HttpClient http, GenerationProperties props, ObjectMapper mapper) {
        this.http = http;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public void stream(List<ChatMessage> messages, Consumer<String> onToken) {
        String body = buildBody(messages);
        int attempt = 0;
        while (true) {
            attempt++;
            HttpResponse<Stream<String>> response;
            try {
                response = http.send(request(body), HttpResponse.BodyHandlers.ofLines());
            } catch (IOException e) {
                if (attempt > props.maxRetries()) {
                    throw new GenerationException("Generation request failed (transport)", e);
                }
                backoff(attempt);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GenerationException("Generation interrupted", e);
            }

            int status = response.statusCode();
            if (status == 200) {
                try (Stream<String> lines = response.body()) {
                    lines.forEach(line -> extractDelta(mapper, line).ifPresent(onToken));
                }
                return;
            }

            String errorBody = response.body().collect(Collectors.joining("\n"));
            boolean retryable = status == 429 || status >= 500;
            if (!retryable || attempt > props.maxRetries()) {
                throw new GenerationException("Generation failed with HTTP " + status
                        + ": " + truncate(errorBody));
            }
            log.warn("Generation stream not established (HTTP {}); retrying", status);
            backoff(attempt);
        }
    }

    private HttpRequest request(String body) {
        return HttpRequest.newBuilder(URI.create(props.baseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String buildBody(List<ChatMessage> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", props.model());
        payload.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        payload.put("stream", true);
        payload.put("temperature", props.temperature());
        payload.put("max_tokens", props.maxOutputTokens());
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new GenerationException("Failed to serialize generation request", e);
        }
    }

    private void backoff(int attempt) {
        Duration delay = Vectors.backoff(attempt, props.initialBackoff(), props.maxBackoff());
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationException("Interrupted during retry backoff", e);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_ERROR_CHARS ? s.substring(0, MAX_ERROR_CHARS) : s;
    }

    /**
     * Parses one SSE line, returning the content delta if present. Lines without
     * a {@code data:} prefix, the {@code [DONE]} sentinel, and deltas without text
     * (e.g. the role-only first frame) all yield empty. Package-private for tests.
     */
    static Optional<String> extractDelta(ObjectMapper mapper, String line) {
        if (line == null || !line.startsWith("data:")) {
            return Optional.empty();
        }
        String payload = line.substring("data:".length()).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return Optional.empty();
        }
        try {
            JsonNode content = mapper.readTree(payload)
                    .path("choices").path(0).path("delta").path("content");
            if (content.isTextual() && !content.asText().isEmpty()) {
                return Optional.of(content.asText());
            }
        } catch (Exception e) {
            // Ignore malformed frames; the stream may include keep-alives/comments.
        }
        return Optional.empty();
    }
}
