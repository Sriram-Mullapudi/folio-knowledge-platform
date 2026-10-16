package com.ekp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Strongly-typed JWT settings bound from {@code ekp.security.jwt.*}.
 *
 * @param secret      base64-encoded HMAC signing key (>= 256 bits). Injected via
 *                    env in every environment; the local default is dev-only.
 * @param issuer      token issuer claim
 * @param accessTtl   lifetime of access tokens (short, e.g. 15m)
 * @param refreshTtl  lifetime of refresh tokens (long, e.g. 30d)
 */
@ConfigurationProperties(prefix = "ekp.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl
) {}
