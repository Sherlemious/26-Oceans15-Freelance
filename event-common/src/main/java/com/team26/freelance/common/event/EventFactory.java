package com.team26.freelance.common.event;

import java.util.Map;
import java.util.Objects;

public class EventFactory {

    private EventFactory() {}

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (Objects.requireNonNull(type, "type must not be null")) {
            case AUTH -> new AuthEvent(params);
            case JOB -> new JobEvent(params);
            case PROPOSAL -> new ProposalEvent(params);
            case CONTRACT -> new ContractEvent(params);
            case PAYOUT_AUDIT -> new PayoutAuditEvent(params);
        };
    }
}
