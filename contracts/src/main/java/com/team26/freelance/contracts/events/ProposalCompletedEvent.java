package com.team26.freelance.contracts.events;

import java.math.BigDecimal;

public record ProposalCompletedEvent(
        Long proposalId,
        Long jobId,
        Long freelancerId,
        Long contractId,
        BigDecimal agreedAmount) {
}
