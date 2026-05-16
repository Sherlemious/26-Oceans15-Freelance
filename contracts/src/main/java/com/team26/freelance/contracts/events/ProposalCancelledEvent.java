package com.team26.freelance.contracts.events;

public record ProposalCancelledEvent(
        Long proposalId,
        Long jobId,
        Long freelancerId,
        String reason) {
}
