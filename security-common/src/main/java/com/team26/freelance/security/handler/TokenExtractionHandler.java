package com.team26.freelance.security.handler;

import com.team26.freelance.security.JwtAuthContext;
import com.team26.freelance.security.JwtAuthException;
import org.springframework.http.HttpHeaders;

public class TokenExtractionHandler extends AbstractJwtHandler {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public void handle(JwtAuthContext context) throws JwtAuthException {
        String authHeader = context.getRequest().getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            throw new JwtAuthException("Missing Authorization header");
        }
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtAuthException("Authorization header must start with 'Bearer '");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new JwtAuthException("Bearer token is empty");
        }

        context.setRawToken(token);
        passToNext(context);
    }
}