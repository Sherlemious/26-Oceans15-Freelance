package com.team26.freelance.proposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

public record CreateProposalMilestoneDTO(
        @NotBlank(message = "title is required")
        String title,
        @NotBlank(message = "description is required")
        String description,
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than 0")
        Double amount,
        Map<String, Object> metadata) {
}
