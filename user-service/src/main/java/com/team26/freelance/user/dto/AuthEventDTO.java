package com.team26.freelance.user.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class AuthEventDTO {

    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public AuthEventDTO() {
    }

    public AuthEventDTO(String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
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
