package com.ekp.rag;

import com.ekp.rag.dto.RagRequest;
import com.ekp.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

/**
 * Asks a question over the user's documents and streams the answer.
 *
 * <p>Availability is checked synchronously so a missing API key returns a clean
 * 503 (handled by the global handler) instead of an empty event stream. The
 * actual work runs on the chat executor; the emitter is returned immediately and
 * fed sources, then answer tokens, then a terminal event.
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Retrieval-augmented answers over your documents")
public class ChatController {

    /** No server-side timeout: answers can legitimately take a while to stream. */
    private static final long NO_TIMEOUT = 0L;

    private final RagService rag;
    private final Executor chatExecutor;

    public ChatController(RagService rag,
                          @Qualifier(GenerationConfig.EXECUTOR) Executor chatExecutor) {
        this.rag = rag;
        this.chatExecutor = chatExecutor;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask a question; streams citations then the answer via SSE")
    public SseEmitter ask(@Valid @RequestBody RagRequest request,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        rag.ensureAvailable(); // -> 503 before we commit to a stream

        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        SseAnswerSink sink = new SseAnswerSink(emitter);
        chatExecutor.execute(() -> rag.answer(request, user, sink));
        return emitter;
    }
}
