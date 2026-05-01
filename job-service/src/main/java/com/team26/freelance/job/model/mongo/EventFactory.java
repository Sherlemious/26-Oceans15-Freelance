package com.team26.freelance.job.model.mongo;

import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (type) {
            case JOB -> new JobEvent(params);
            default -> throw new UnsupportedOperationException("EventType " + type + " is not supported in job-service");
        };
    }
}
