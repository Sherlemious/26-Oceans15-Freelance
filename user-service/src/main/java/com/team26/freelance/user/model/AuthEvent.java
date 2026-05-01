package com.team26.freelance.user.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "auth_events")
public class AuthEvent implements MongoEvent {
    @Id
    private String id;

    private Long userId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public AuthEvent(Long userId, String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.userId = userId;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    public AuthEvent(Map<String, Object> params) {
        Object rawId = params.get("userId");
        this.userId = rawId instanceof Number n ? n.longValue() : null;
        this.action = (String) params.get("action");
        Object ts = params.get("timestamp");
        this.timestamp = ts instanceof LocalDateTime ldt ? ldt : LocalDateTime.now();
        Object det = params.get("details");
        this.details = det instanceof Map<?, ?> m ? (Map<String, Object>) m : new java.util.HashMap<>(params);
    }

    public String getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
