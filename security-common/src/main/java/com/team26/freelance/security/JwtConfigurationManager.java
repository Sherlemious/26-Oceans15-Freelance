package com.team26.freelance.security;

public class JwtConfigurationManager {

    private static final String DEFAULT_SECRET = "c2VjdXJlLWRlZmF1bHQta2V5LWZvci1kZXZlbG9wbWVudC1vbmx5";
    private static final long DEFAULT_EXPIRATION_MS = 86400000L;

    private static volatile JwtConfigurationManager instance;

    private final String secret;
    private final long expirationMs;

    private JwtConfigurationManager() {
        this.secret = valueOrDefault(System.getenv("JWT_SECRET"), DEFAULT_SECRET);
        this.expirationMs = parseExpiration(valueOrDefault(
                System.getenv("JWT_EXPIRATION"),
                String.valueOf(DEFAULT_EXPIRATION_MS)
        ));
    }

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

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public long getExpiration() {
        return expirationMs;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static long parseExpiration(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return DEFAULT_EXPIRATION_MS;
        }
    }
}
