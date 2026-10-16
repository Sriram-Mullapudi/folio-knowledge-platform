package com.ekp.embedding;

/** Thrown when a feature needs embeddings but they are disabled / unconfigured. */
public class EmbeddingDisabledException extends RuntimeException {
    public EmbeddingDisabledException(String message) {
        super(message);
    }
}
