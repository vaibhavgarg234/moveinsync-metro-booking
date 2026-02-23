package com.moveinsync.metro.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

/**
 * JWT Utility
 * -----------
 * Issues and validates HS256-signed JWTs.
 *
 * Token payload:
 *   sub   → username
 *   userId→ UUID of user
 *   roles → comma-separated roles
 *   iat   → issued at
 *   exp   → expiry (configurable, default 15 min)
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${metro.jwt.secret:moveinsync-jwt-secret-key-must-be-256-bits-long!!}")
    private String secret;

    @Value("${metro.jwt.expiry-ms:900000}") // 15 minutes default
    private long expiryMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, String userId, Set<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", String.join(",", roles))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key())
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String getUsername(String token) {
        return validateToken(token).getSubject();
    }

    public String getUserId(String token) {
        return validateToken(token).get("userId", String.class);
    }
}