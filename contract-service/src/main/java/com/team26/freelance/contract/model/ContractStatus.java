package com.team26.freelance.contract.model;

import java.util.List;
import java.util.Map;

public enum ContractStatus {
    ACTIVE,
    COMPLETED,
    TERMINATED,
    DISPUTED;

    private static final Map<ContractStatus, List<ContractStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, List.of(COMPLETED, TERMINATED, DISPUTED),
            DISPUTED, List.of(COMPLETED, TERMINATED),
            COMPLETED, List.of(),
            TERMINATED, List.of()
    );

    public boolean isValidTransitionTo(ContractStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, List.of()).contains(next);
    }
}
