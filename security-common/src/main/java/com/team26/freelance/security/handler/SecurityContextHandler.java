package com.team26.freelance.security.handler;

import com.team26.freelance.security.JwtAuthContext;
import com.team26.freelance.security.JwtAuthException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public class SecurityContextHandler extends AbstractJwtHandler {

    @Override
    public void handle(JwtAuthContext context) throws JwtAuthException {
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + context.getRole().toUpperCase()));

        var authentication = new UsernamePasswordAuthenticationToken(
                context.getSubject(),
                context.getUid(),
                authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        // Final handler — no passToNext
    }
}