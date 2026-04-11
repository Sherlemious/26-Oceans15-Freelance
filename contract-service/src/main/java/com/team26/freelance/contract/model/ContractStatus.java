package com.team26.freelance.contract.model;

import java.util.List;
import java.util.Map;

public enum ContractStatus {
    ACTIVE,
    COMPLETED,
    TERMINATED,
    DISPUTED;

    private static final Map<ContractStatus, List<ContractStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, List.of(ACTIVE, COMPLETED, TERMINATED, DISPUTED),
            DISPUTED, List.of(DISPUTED, ACTIVE, TERMINATED),
            COMPLETED, List.of(COMPLETED),
            TERMINATED, List.of(TERMINATED)
    );

    public boolean isValidTransitionTo(ContractStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, List.of()).contains(next);
    }
}
