package com.team26.freelance.proposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

import com.team26.freelance.proposal.model.ProposalStatus;

public record UpdateProposalDTO(
                @NotBlank(message = "coverLetter is required") String coverLetter,
                @NotNull(message = "bidAmount is required") @Positive(message = "bidAmount must be positive") Double bidAmount,
                @NotNull(message = "estimatedDays is required") @Positive(message = "estimatedDays must be positive") Integer estimatedDays,
                Map<String, Object> metadata,
                ProposalStatus status) {

}
