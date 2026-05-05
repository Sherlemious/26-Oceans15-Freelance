package com.team26.freelance.security.handler;

import com.team26.freelance.security.JwtAuthContext;
import com.team26.freelance.security.JwtAuthException;
import io.jsonwebtoken.Claims;

public class ClaimsExtractionHandler extends AuthHandler {

    @Override
    public void handle(JwtAuthContext context) throws JwtAuthException {
        Claims claims = (Claims) context.getRequest().getAttribute("jwt_claims");

        if (claims == null) {
            throw new JwtAuthException("Claims not found — TokenValidationHandler must run first");
        }

        String subject = claims.getSubject();
        Object uidClaim = claims.get("uid");
        String uid = uidClaim == null ? null : String.valueOf(uidClaim);
        String role = claims.get("role", String.class);

        if (subject == null || subject.isBlank())
            throw new JwtAuthException("Missing 'sub' claim");
        if (uid == null || uid.isBlank())
            throw new JwtAuthException("Missing 'uid' claim");
        if (role == null || role.isBlank())
            throw new JwtAuthException("Missing 'role' claim");

        context.setSubject(subject);
        context.setUid(uid);
        context.setRole(role);

        passToNext(context);
    }
}