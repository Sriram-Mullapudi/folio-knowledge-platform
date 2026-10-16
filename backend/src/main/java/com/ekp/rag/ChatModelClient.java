package com.ekp.rag;

import java.util.List;
import java.util.function.Consumer;

/**
 * Streams a chat completion. Each content delta is delivered to {@code onToken}
 * as it arrives; the call returns when the stream ends and throws
 * {@link GenerationException} on failure.
 */
public interface ChatModelClient {
    void stream(List<ChatMessage> messages, Consumer<String> onToken);
}
