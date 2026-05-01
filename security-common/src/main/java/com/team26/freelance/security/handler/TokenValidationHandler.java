package com.team26.freelance.security.handler;

import com.team26.freelance.security.JwtAuthContext;
import com.team26.freelance.security.JwtAuthException;
import com.team26.freelance.security.JwtConfigurationManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class TokenValidationHandler extends AbstractJwtHandler {

    private final JwtConfigurationManager config;

    public TokenValidationHandler(JwtConfigurationManager config) {
        this.config = config;
    }

    @Override
    public void handle(JwtAuthContext context) throws JwtAuthException {
        try {
            SecretKey key = buildSigningKey();

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(context.getRawToken())
                    .getPayload();

            context.getRequest().setAttribute("jwt_claims", claims);

        } catch (ExpiredJwtException e) {
            throw new JwtAuthException("JWT token has expired");
        } catch (JwtException e) {
            throw new JwtAuthException("JWT signature validation failed: " + e.getMessage());
        }

        passToNext(context);
    }

    private SecretKey buildSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(config.getSecret());
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}