package com.team26.freelance.contract.model;

import java.util.List;
import java.util.Map;

public enum ContractStatus {
    ACTIVE,
    COMPLETED,
    TERMINATED,
    DISPUTED;

    public boolean isValidTransitionTo(ContractStatus next) {
        Map<ContractStatus, ContractStatus[]> validTransitions = Map.of(
                ACTIVE, new ContractStatus[]{COMPLETED, TERMINATED, DISPUTED},
                DISPUTED, new ContractStatus[]{COMPLETED, TERMINATED},
                COMPLETED, new ContractStatus[]{},
                TERMINATED, new ContractStatus[]{}
        );

        return List.of(validTransitions.get(this)).contains(next);
    }
}
