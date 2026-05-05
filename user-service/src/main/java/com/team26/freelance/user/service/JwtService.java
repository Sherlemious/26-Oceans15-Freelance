package com.team26.freelance.user.service;

import com.team26.freelance.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Long expiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") Long expiration) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiration = expiration;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateAndExtractClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUidFromClaims(Claims claims) {
        Object uidObj = claims.get("uid");
        if (uidObj instanceof String) {
            return Long.parseLong((String) uidObj);
        }
        return ((Number) uidObj).longValue();
    }

    public String getRoleFromClaims(Claims claims) {
        return claims.get("role", String.class);
    }

    public Long getExpiration() {
        return expiration;
    }
}
