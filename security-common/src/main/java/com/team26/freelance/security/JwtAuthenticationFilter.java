package com.team26.freelance.security;

import com.team26.freelance.security.handler.AuthHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthHandler chainHead;

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/users/health",
            "/api/jobs/health",
            "/api/contracts/health",
            "/api/proposals/health",
            "/api/wallets/health"
    );

    public JwtAuthenticationFilter(AuthHandler chainHead) {
        this.chainHead = chainHead;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return EXEMPT_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            JwtAuthContext context = new JwtAuthContext(request);
            chainHead.handle(context);
            filterChain.doFilter(request, response);

        } catch (JwtAuthException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\": \"Unauthorized\", \"message\": \"" + ex.getMessage() + "\"}"
            );
        }
    }
}