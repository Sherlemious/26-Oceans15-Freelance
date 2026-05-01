package com.team26.freelance.proposal.model;

import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (type) {
            case PROPOSAL -> new ProposalEvent(params);
            default -> throw new UnsupportedOperationException("EventType " + type + " is not supported in proposal-service");
        };
    }
}
