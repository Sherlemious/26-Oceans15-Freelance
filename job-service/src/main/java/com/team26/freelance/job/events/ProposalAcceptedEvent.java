package com.team26.freelance.job.events;

import java.math.BigDecimal;

public record ProposalAcceptedEvent(Long proposalId, Long jobId, Long freelancerId, BigDecimal bidAmount) {}
