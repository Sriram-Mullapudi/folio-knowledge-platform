package com.ekp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Note there is deliberately NO role field: a client can
 * never choose its own role. New accounts are always USER; ADMIN is granted out
 * of band (seeded on startup, or by an existing admin in a later phase).
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {}
