package com.team26.freelance.user.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "auth_events")
public class AuthEvent {
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
