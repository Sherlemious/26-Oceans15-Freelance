package com.team26.freelance.user.model;

import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (type) {
            case AUTH -> new AuthEvent(params);
            default -> throw new UnsupportedOperationException("EventType " + type + " is not supported in user-service");
        };
    }
}
