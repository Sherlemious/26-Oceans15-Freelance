package com.team26.freelance.user.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class UserActivityDTO {
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public UserActivityDTO(String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return details; }

    public static class Builder {
        private String action;
        private LocalDateTime timestamp;
        private Map<String, Object> details;

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public UserActivityDTO build() {
            return new UserActivityDTO(action, timestamp, details);
        }
    }
}
