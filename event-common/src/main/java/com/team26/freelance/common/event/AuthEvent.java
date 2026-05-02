package com.team26.freelance.common.event;

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

    public AuthEvent() {}

    public AuthEvent(Map<String, Object> params) {
        this.id = EventParams.stringValue(params, "id");
        this.userId = EventParams.longValue(params, "userId");
        this.action = EventParams.stringValue(params, "action");
        this.timestamp = EventParams.timestampValue(params);
        this.details = EventParams.detailsValue(params);
    }

    @Override
    public String getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public Map<String, Object> getDetails() {
        return details;
    }
}
