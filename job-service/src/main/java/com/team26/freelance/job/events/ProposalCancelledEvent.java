package com.team26.freelance.job.events;

public record ProposalCancelledEvent(Long proposalId, Long jobId, Long freelancerId, String reason) {}
