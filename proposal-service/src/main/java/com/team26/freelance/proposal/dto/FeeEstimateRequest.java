package com.team26.freelance.proposal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record FeeEstimateRequest(
        @NotNull(message = "bidAmount is required")
        @Positive(message = "bidAmount must be positive")
        Double bidAmount,
        @NotNull(message = "estimatedDays is required")
        @PositiveOrZero(message = "estimatedDays must be zero or positive")
        Integer estimatedDays) {
}
