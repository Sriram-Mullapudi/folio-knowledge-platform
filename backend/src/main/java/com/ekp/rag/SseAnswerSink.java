package com.ekp.rag;

import com.ekp.rag.dto.SourceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Bridges {@link AnswerSink} to Server-Sent Events. Emits named JSON events:
 * {@code sources} (citation list), {@code token} ({@code {"delta": "..."}}),
 * {@code done}, and {@code error} ({@code {"message": "..."}}). JSON payloads
 * keep newlines/quotes safely escaped on the wire.
 *
 * <p>If the client has disconnected, sends fail; we mark the stream closed and
 * stop rather than letting the exception escape the worker thread.
 */
class SseAnswerSink implements AnswerSink {

    private static final Logger log = LoggerFactory.getLogger(SseAnswerSink.class);

    private final SseEmitter emitter;
    private volatile boolean closed;

    SseAnswerSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onSources(List<SourceResponse> sources) {
        send("sources", sources);
    }

    @Override
    public void onToken(String delta) {
        send("token", Map.of("delta", delta));
    }

    @Override
    public void onComplete() {
        send("done", Map.of());
        complete();
    }

    @Override
    public void onError(Throwable error) {
        // Disabled-state errors are screened out before streaming begins, so by
        // here a failure is a transport/model error: report it generically.
        send("error", Map.of("message", "Answer generation failed. Please try again."));
        complete();
    }

    private void send(String event, Object data) {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Client gone or emitter already completed; stop quietly.
            closed = true;
            log.debug("SSE send failed ({}); closing stream", ex.getMessage());
        }
    }

    private void complete() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Already completed/aborted.
        }
    }
}
