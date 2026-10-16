package com.ekp.rag;

import com.ekp.rag.dto.SourceResponse;

import java.util.List;

/**
 * Transport-agnostic callback for streaming an answer. The controller adapts it
 * to Server-Sent Events; tests can use a simple recording implementation.
 * Contract: {@code onSources} once, then {@code onToken} zero or more times,
 * then exactly one of {@code onComplete} / {@code onError}.
 */
public interface AnswerSink {
    void onSources(List<SourceResponse> sources);
    void onToken(String delta);
    void onComplete();
    void onError(Throwable error);
}
