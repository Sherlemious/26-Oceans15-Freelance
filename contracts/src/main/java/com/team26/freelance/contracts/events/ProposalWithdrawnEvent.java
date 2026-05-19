package com.team26.freelance.contracts.events;

public record ProposalWithdrawnEvent(Long proposalId, Long jobId, Long freelancerId, int remainingActiveProposals) {
}
