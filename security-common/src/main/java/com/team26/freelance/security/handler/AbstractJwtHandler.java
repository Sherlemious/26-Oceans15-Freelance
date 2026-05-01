package com.team26.freelance.security.handler;

import com.team26.freelance.security.JwtAuthContext;
import com.team26.freelance.security.JwtAuthException;

public abstract class AbstractJwtHandler {

    private AbstractJwtHandler next;

    public AbstractJwtHandler setNext(AbstractJwtHandler next) {
        this.next = next;
        return next;
    }

    public abstract void handle(JwtAuthContext context) throws JwtAuthException;

    protected void passToNext(JwtAuthContext context) throws JwtAuthException {
        if (next != null) {
            next.handle(context);
        }
    }
}