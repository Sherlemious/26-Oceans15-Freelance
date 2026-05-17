package com.team26.freelance.job.events;

public record ProposalWithdrawnEvent(Long proposalId, Long jobId, Long freelancerId) {}
