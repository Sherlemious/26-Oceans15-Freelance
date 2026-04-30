package com.team26.freelance.proposal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AddMilestoneToProposalDTO(
        @NotEmpty(message = "milestones is required")
        List<@Valid AddMilestoneToProposalItemDTO> milestones) {
}