package com.team26.freelance.security;

import jakarta.servlet.http.HttpServletRequest;

public class JwtAuthContext {

    private final HttpServletRequest request;
    private String rawToken;
    private String subject;
    private String uid;
    private String role;

    public JwtAuthContext(HttpServletRequest request) {
        this.request = request;
    }

    public HttpServletRequest getRequest() { return request; }
    public String getRawToken()            { return rawToken; }
    public String getSubject()             { return subject; }
    public String getUid()                 { return uid; }
    public String getRole()                { return role; }

    public void setRawToken(String rawToken) { this.rawToken = rawToken; }
    public void setSubject(String subject)   { this.subject = subject; }
    public void setUid(String uid)           { this.uid = uid; }
    public void setRole(String role)         { this.role = role; }
}