package com.ekp.common.error;

/** Thrown when a request conflicts with existing state (e.g. duplicate email). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
