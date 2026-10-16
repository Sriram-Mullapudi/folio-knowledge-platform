package com.ekp.auth.dto;

/**
 * Returned on successful login/refresh. The access token is a short-lived JWT
 * sent on every request; the refresh token is an opaque, long-lived credential
 * exchanged only at /auth/refresh.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {
    public static AuthResponse bearer(String accessToken, String refreshToken,
                                      long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
