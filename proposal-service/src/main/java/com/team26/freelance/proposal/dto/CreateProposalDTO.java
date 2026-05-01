package com.team26.freelance.proposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

public record CreateProposalDTO(
        @NotNull(message = "jobId is required")
        Long jobId,
        @NotNull(message = "freelancerId is required")
        Long freelancerId,
        @NotBlank(message = "coverLetter is required")
        String coverLetter,
        @NotNull(message = "bidAmount is required")
        @Positive(message = "bidAmount must be positive")
        Double bidAmount,
        @NotNull(message = "estimatedDays is required")
        @Positive(message = "estimatedDays must be positive")
        Integer estimatedDays,
        Map<String, Object> metadata) {
}
