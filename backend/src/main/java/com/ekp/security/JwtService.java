package com.ekp.security;

import com.ekp.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies short-lived access tokens (HMAC-SHA256). The token subject
 * is the user id; email and role travel as claims so authorization needs no DB
 * lookup on every request.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(JwtProperties props) {
        // Accept either a base64 secret or a raw string; require >= 32 bytes.
        byte[] keyBytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "ekp.security.jwt.secret must be at least 32 bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = props.issuer();
        this.accessTtlSeconds = props.accessTtl().getSeconds();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    /**
     * Validates signature + expiry and returns the parsed claims, or empty when
     * the token is malformed, expired or tampered with. Never throws to callers.
     */
    public Optional<ParsedToken> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .requireIssuer(issuer)
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new ParsedToken(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_ROLE, String.class)
            ));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record ParsedToken(UUID userId, String email, String role) {}
}
