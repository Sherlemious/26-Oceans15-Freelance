package com.team26.freelance.proposal.dto;

import java.util.Map;

import com.team26.freelance.proposal.model.MilestoneStatus;

public record ProposalMilestoneDTO(Long id, int milestoneOrder, String title, String description, Double amount,
        MilestoneStatus status, Map<String, Object> metadata) {

}
