package com.team26.freelance.proposal.dto;

import java.util.Map;

public record CreateProposalMilestoneDTO(String title, String description, Double amount,
        Map<String, Object> metadata) {

    public CreateProposalMilestoneDTO {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required and must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required and must not be blank");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount is required and must be greater than 0");
        }
    }

}
