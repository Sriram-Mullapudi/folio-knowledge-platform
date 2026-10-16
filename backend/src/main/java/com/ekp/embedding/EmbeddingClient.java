package com.ekp.embedding;

import java.util.List;

/**
 * Provider-agnostic embedding client. One call embeds a batch of inputs and
 * returns one vector per input, in the same order.
 */
public interface EmbeddingClient {

    List<float[]> embed(List<String> inputs);

    /** Thrown when embedding cannot be performed (transport, auth, or quota). */
    class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message) {
            super(message);
        }

        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
