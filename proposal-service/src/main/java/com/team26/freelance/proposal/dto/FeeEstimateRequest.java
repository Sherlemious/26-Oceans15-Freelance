package com.team26.freelance.proposal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class FeeEstimateRequest {
    @NotNull(message = "jobId is required")
    @Positive(message = "jobId must be positive")
    private Long jobId;

    @NotNull(message = "bidAmount is required")
    @Positive(message = "bidAmount must be positive")
    private Double bidAmount;

    @NotNull(message = "competingProposals is required")
    @PositiveOrZero(message = "competingProposals must be zero or positive")
    private Integer competingProposals;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Double getBidAmount() { return bidAmount; }
    public void setBidAmount(Double bidAmount) { this.bidAmount = bidAmount; }
    public Integer getCompetingProposals() { return competingProposals; }
    public void setCompetingProposals(Integer competingProposals) { this.competingProposals = competingProposals; }
}