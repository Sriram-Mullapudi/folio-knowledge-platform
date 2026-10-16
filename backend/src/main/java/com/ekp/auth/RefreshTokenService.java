package com.ekp.auth;

import com.ekp.security.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages opaque refresh tokens with rotation.
 *
 * <p>Design: the raw token is 256 bits of randomness, returned to the client
 * once and never stored. We persist only its SHA-256 hash. On refresh we look up
 * by hash, verify it is active, revoke it, and issue a fresh one (rotation),
 * which limits the blast radius of a leaked token.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final long refreshTtlSeconds;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties props) {
        this.repository = repository;
        this.refreshTtlSeconds = props.refreshTtl().getSeconds();
    }

    /** Issues a new refresh token for the user and returns the RAW value. */
    @Transactional
    public String issue(UUID userId) {
        String raw = generateRawToken();
        RefreshToken entity = new RefreshToken(
                userId, hash(raw), Instant.now().plusSeconds(refreshTtlSeconds));
        repository.save(entity);
        return raw;
    }

    /**
     * Validates and rotates a refresh token. Returns the owning user id and a new
     * raw refresh token, or empty if the supplied token is unknown/expired/revoked.
     */
    @Transactional
    public Optional<Rotation> rotate(String rawToken) {
        return repository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isActive)
                .map(token -> {
                    token.setRevoked(true);        // rotation: old token dies
                    String newRaw = issue(token.getUserId());
                    return new Rotation(token.getUserId(), newRaw);
                });
    }

    /** Revokes a single token (logout). Idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    /** Revokes every active token for a user (e.g. password change, later phase). */
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Rotation(UUID userId, String newRefreshToken) {}
}
