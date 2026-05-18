package com.team26.freelance.contracts.events;

import java.math.BigDecimal;

public record ProposalAcceptedEvent(
        Long proposalId,
        Long jobId,
        Long clientId,
        Long freelancerId,
        BigDecimal bidAmount) {
}
