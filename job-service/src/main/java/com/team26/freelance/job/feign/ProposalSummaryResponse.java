package com.team26.freelance.job.feign;

public record ProposalSummaryResponse(
        Long totalProposals,
        Double averageBidAmount,
        Double lowestBid,
        Double highestBid,
        Long acceptedProposals) {}