package com.team26.freelance.wallet.model;

import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (type) {
            case PAYOUT_AUDIT -> new PayoutAuditEvent(params);
            default -> throw new UnsupportedOperationException("EventType " + type + " is not supported in wallet-service");
        };
    }
}
