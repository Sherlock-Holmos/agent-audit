package com.audit.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtService(
        @Value("${auth.jwt.secret}") String secret,
        @Value("${auth.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Instant expireAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(username)
            .claims(Map.of("role", role))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expireAt))
            .signWith(secretKey)
            .compact();
    }

    public String parseUsername(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }
}
