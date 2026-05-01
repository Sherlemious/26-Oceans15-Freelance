package com.team26.freelance.contract.model.mongo;

import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (type) {
            case CONTRACT -> new ContractEvent(params);
            default -> throw new UnsupportedOperationException("EventType " + type + " is not supported in contract-service");
        };
    }
}
