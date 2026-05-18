package com.team26.freelance.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final String DEFAULT_SECRET = "c2VjdXJlLWRlZmF1bHQta2V5LWZvci1kZXZlbG9wbWVudC1vbmx5";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth",
            "/api/users/health",
            "/api/jobs/health",
            "/api/proposals/health",
            "/api/contracts/health",
            "/api/payouts/health",
            "/actuator"
    );

    private final SecretKey signingKey;

    public JwtGatewayFilter(@Value("${jwt.secret:" + DEFAULT_SECRET + "}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange correlatedExchange = withCorrelationId(exchange);

        if (isPublicPath(correlatedExchange.getRequest().getURI().getPath())) {
            return chain.filter(correlatedExchange);
        }

        String token = extractBearerToken(correlatedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return unauthorized(correlatedExchange);
        }

        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (ExpiredJwtException ex) {
            return unauthorized(correlatedExchange);
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(correlatedExchange);
        }

        String userId = stringify(claims.get("uid"));
        String userRole = claims.get("role", String.class);
        if (isBlank(userId) || isBlank(userRole)) {
            return unauthorized(correlatedExchange);
        }

        ServerHttpRequest authenticatedRequest = correlatedExchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.set(USER_ID_HEADER, userId);
                    headers.set(USER_ROLE_HEADER, userRole);
                })
                .build();

        return chain.filter(correlatedExchange.mutate().request(authenticatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private ServerWebExchange withCorrelationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (isBlank(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        String resolvedCorrelationId = correlationId;

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.set(CORRELATION_ID_HEADER, resolvedCorrelationId);
                })
                .build();

        return exchange.mutate().request(request).build();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path)
                || path.startsWith("/api/auth/")
                || path.startsWith("/actuator/");
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
