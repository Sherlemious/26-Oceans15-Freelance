package com.team26.freelance.user.service;

import com.team26.freelance.security.JwtConfigurationManager;
import com.team26.freelance.user.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Long expiration;

    public JwtService() {
        JwtConfigurationManager jwtConfigurationManager = JwtConfigurationManager.getInstance();
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtConfigurationManager.getSecret()));
        this.expiration = jwtConfigurationManager.getExpiration();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Long getExpiration() {
        return expiration;
    }
}
