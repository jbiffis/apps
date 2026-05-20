package com.biffis.tracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies HS256 JWTs. Payload: {@code { sub: userId, username, exp }}.
 * Secret comes from {@code tracker.jwt.secret} (env {@code JWT_SECRET}) and must
 * be at least 32 bytes for HS256.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlDays;

    public JwtService(
            @Value("${tracker.jwt.secret}") String secret,
            @Value("${tracker.jwt.ttl-days:30}") long ttlDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "tracker.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlDays = ttlDays;
    }

    public String issue(UUID userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttlDays, ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * Parse + verify a token. Returns the principal on success, empty on any
     * failure (bad signature, expired, malformed). Never throws to the caller.
     */
    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID id = UUID.fromString(claims.getSubject());
            String username = claims.get("username", String.class);
            return Optional.of(new AuthUser(id, username));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
