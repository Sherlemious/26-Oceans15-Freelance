package com.team26.freelance.proposal.dto;

import java.util.Map;

public record CreateProposalDTO(Long jobId, Long freelancerId, String coverLetter, Double bidAmount,
        Integer estimatedDays, Map<String, Object> metadata) {

}
