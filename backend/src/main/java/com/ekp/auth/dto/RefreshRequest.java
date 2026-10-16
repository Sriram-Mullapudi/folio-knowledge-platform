package com.ekp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Carries the opaque refresh token for /refresh and /logout. */
public record RefreshRequest(
        @NotBlank String refreshToken
) {}
