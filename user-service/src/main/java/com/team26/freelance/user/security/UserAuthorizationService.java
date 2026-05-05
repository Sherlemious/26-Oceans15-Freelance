package com.team26.freelance.user.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserAuthorizationService {

    public void requireOwnerOrAdmin(Long targetUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }

        Long authenticatedUserId = authenticatedUserId(authentication);
        if (targetUserId != null && targetUserId.equals(authenticatedUserId)) {
            return;
        }
        if (hasAdminRole(authentication)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User access denied");
    }

    private Long authenticatedUserId(Authentication authentication) {
        Object credentials = authentication.getCredentials();
        if (credentials == null) {
            throw unauthorized();
        }

        try {
            return Long.valueOf(credentials.toString());
        } catch (NumberFormatException ex) {
            throw unauthorized();
        }
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid user authentication");
    }
}
