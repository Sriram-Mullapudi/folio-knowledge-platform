package com.ekp.rag;

/** Thrown when the chat model call fails (transport, auth, or server error). */
public class GenerationException extends RuntimeException {
    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
