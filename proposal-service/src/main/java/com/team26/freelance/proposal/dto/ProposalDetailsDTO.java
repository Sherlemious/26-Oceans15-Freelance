package com.team26.freelance.proposal.dto;

import java.util.List;
import java.util.Map;

import com.team26.freelance.proposal.model.ProposalStatus;

public record ProposalDetailsDTO(Long proposalId, Long jobId, Long freelancerId, ProposalStatus status,
        Double bidAmount, Map<String, Object> metadata, List<ProposalMilestoneDTO> milestones, int totalMilestones,
        int completedMilestones) {

}
