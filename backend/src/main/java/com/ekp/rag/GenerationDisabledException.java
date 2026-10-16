package com.ekp.rag;

/** Thrown when answer generation is requested but not configured (no API key). */
public class GenerationDisabledException extends RuntimeException {
    public GenerationDisabledException(String message) {
        super(message);
    }
}
