package com.ekp.common.error;

/**
 * Thrown when a requested resource does not exist. Mapped to HTTP 404 by
 * {@link GlobalExceptionHandler}. Feature modules in later phases reuse this
 * rather than each inventing their own not-found signal.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found: %s".formatted(resource, id));
    }
}
