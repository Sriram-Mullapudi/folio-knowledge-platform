package com.ekp.common.error;

import com.ekp.common.web.CorrelationIdFilter;
import com.ekp.embedding.EmbeddingDisabledException;
import com.ekp.rag.GenerationDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.List;

/**
 * Centralised exception handling. Every error leaves the application as a
 * consistent {@link ApiError}; unexpected errors are logged with their stack
 * trace server-side but never expose internals to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(org.springframework.web.server.ResponseStatusException ex, WebRequest request) {
        return build(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), request, List.of());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformed(WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request body", request, List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler({EmbeddingDisabledException.class, GenerationDisabledException.class})
    public ResponseEntity<ApiError> handleServiceDisabled(RuntimeException ex, WebRequest request) {
        // The feature is correctly built but not configured (no API key) — 503,
        // not 500, so clients can tell a config gap from a real failure.
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    /** Bad credentials on login, invalid refresh token, etc. -> 401. */
    @ExceptionHandler({AuthenticationException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ApiError> handleAuth(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials", request, List.of());
    }

    /**
     * Method-security denials (@PreAuthorize) surface here as AccessDeniedException;
     * handle explicitly so the catch-all below never turns a 403 into a 500.
     * URL-level denials are handled earlier by RestAccessDeniedHandler.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource", request, List.of());
    }

    /** Upload exceeds the configured multipart limit -> 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex, WebRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file is too large", request, List.of());
    }

    /** Missing 'file' part or query parameter -> 400. */
    @ExceptionHandler({MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleMissingPart(Exception ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    /**
     * Catch-all. We log the full exception (with stack trace) for diagnosis but
     * return a generic message so we never leak implementation details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request, List.of());
    }

    private ApiError.FieldError toFieldError(FieldError fe) {
        return new ApiError.FieldError(fe.getField(), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           WebRequest request, List<ApiError.FieldError> fieldErrors) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                extractPath(request),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                fieldErrors
        );
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
    }

    private String extractPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            HttpServletRequest http = swr.getRequest();
            return http.getRequestURI();
        }
        return "";
    }
}
