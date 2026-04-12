package com.team26.freelance.proposal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class FeeEstimateRequest {
    @NotNull(message = "bidAmount is required")
    @Positive(message = "bidAmount must be positive")
    private Double bidAmount;
    @NotNull(message = "estimatedDays is required")
    @Positive(message = "estimatedDays must be positive")
    private Integer estimatedDays;

    public Double getBidAmount() { return bidAmount; }
    public void setBidAmount(Double bidAmount) { this.bidAmount = bidAmount; }
    public Integer getEstimatedDays() { return estimatedDays; }
    public void setEstimatedDays(Integer estimatedDays) { this.estimatedDays = estimatedDays; }
}