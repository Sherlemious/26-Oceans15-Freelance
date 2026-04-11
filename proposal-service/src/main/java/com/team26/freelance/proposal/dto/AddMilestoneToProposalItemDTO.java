package com.team26.freelance.proposal.dto;

import java.util.Objects;

public record AddMilestoneToProposalItemDTO(
		String title,
		String description,
		Double amount
) {
	public AddMilestoneToProposalItemDTO {
		Objects.requireNonNull(title, "title is required");
		Objects.requireNonNull(description, "description is required");
		Objects.requireNonNull(amount, "amount is required");

		if (title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		if (description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("amount must be greater than 0");
		}
	}
}
