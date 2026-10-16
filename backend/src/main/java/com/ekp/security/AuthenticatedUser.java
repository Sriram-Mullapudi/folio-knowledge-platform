package com.ekp.security;

import java.util.UUID;

/**
 * Lightweight principal derived purely from validated JWT claims, so authorizing
 * a request needs no database round-trip. Injected into controllers via
 * {@code @AuthenticationPrincipal AuthenticatedUser}.
 */
public record AuthenticatedUser(UUID id, String email, String role) {}
