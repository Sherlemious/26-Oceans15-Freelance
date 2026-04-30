package com.team26.freelance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new JwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @jakarta.annotation.PostConstruct
    private void syncStaticInstance() {
        instance = this;
    }

    public String getSecret()      { return secret; }
    public long getExpirationMs()  { return expirationMs; }
}