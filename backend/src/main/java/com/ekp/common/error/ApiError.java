package com.ekp.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error payload returned by {@link GlobalExceptionHandler} for every
 * failed request. A single, predictable shape means the frontend has exactly
 * one error contract to handle.
 *
 * @param timestamp   when the error was produced (UTC)
 * @param status      HTTP status code
 * @param error       HTTP reason phrase
 * @param message     human-readable, client-safe description
 * @param path        request path that failed
 * @param correlationId request id for cross-referencing logs
 * @param fieldErrors per-field validation errors (omitted when empty)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}
}
