package com.team26.freelance.security.handler;

import com.team26.freelance.security.JwtAuthContext;
import com.team26.freelance.security.JwtAuthException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;

public class SecurityContextHandler extends AbstractJwtHandler {

    // ✅ Explicitly saves context — required in Spring Security 6
    private static final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @Override
    public void handle(JwtAuthContext context) throws JwtAuthException {
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + context.getRole().toUpperCase()));

        var authentication = new UsernamePasswordAuthenticationToken(
                context.getSubject(),
                context.getUid(),
                authorities);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        securityContextRepository.saveContext(
                securityContext,
                context.getRequest(),
                (jakarta.servlet.http.HttpServletResponse)
                        context.getRequest().getAttribute("__response")
        );
    }
}