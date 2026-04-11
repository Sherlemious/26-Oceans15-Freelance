package com.team26.freelance.proposal.dto;

import java.util.List;
import java.util.Objects;

public record AddMilestoneToProposalDTO(
    List<AddMilestoneToProposalItemDTO> milestones
) {
    public AddMilestoneToProposalDTO {
        Objects.requireNonNull(milestones, "milestones is required");
        if (milestones.isEmpty()) {
            throw new IllegalArgumentException("milestones must not be empty");
        }
        if (milestones.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("milestones must not contain null items");
        }
    }
}