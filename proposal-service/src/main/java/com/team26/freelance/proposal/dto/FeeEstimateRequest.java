package com.team26.freelance.proposal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class FeeEstimateRequest {
    @NotNull(message = "bidAmount is required")
    @Positive(message = "bidAmount must be positive")
    private Double bidAmount;

    @NotNull(message = "estimatedDays is required")
    @PositiveOrZero(message = "estimatedDays must be zero or positive")
    private Integer estimatedDays;

    public Double getBidAmount() { return bidAmount; }
    public void setBidAmount(Double bidAmount) { this.bidAmount = bidAmount; }
    public Integer getEstimatedDays() { return estimatedDays; }
    public void setEstimatedDays(Integer estimatedDays) { this.estimatedDays = estimatedDays; }
}