package com.ekp.common.error;

/** Thrown when a refresh token is unknown, expired or already revoked. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
