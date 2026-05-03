package com.team26.freelance.proposal.dto;

import java.util.List;
import java.util.Map;

import com.team26.freelance.proposal.model.ProposalStatus;

public class ProposalDetailsDTOBuilder {

    private Long proposalId;
    private Long jobId;
    private Long freelancerId;
    private ProposalStatus status;
    private Double bidAmount;
    private Map<String, Object> metadata;
    private List<ProposalMilestoneDTO> milestones;
    private int totalMilestones;
    private int completedMilestones;

    private ProposalDetailsDTOBuilder() {}

    public static ProposalDetailsDTOBuilder builder() {
        return new ProposalDetailsDTOBuilder();
    }

    public ProposalDetailsDTOBuilder withProposalId(Long proposalId) {
        this.proposalId = proposalId;
        return this;
    }

    public ProposalDetailsDTOBuilder withJobId(Long jobId) {
        this.jobId = jobId;
        return this;
    }

    public ProposalDetailsDTOBuilder withFreelancerId(Long freelancerId) {
        this.freelancerId = freelancerId;
        return this;
    }

    public ProposalDetailsDTOBuilder withStatus(ProposalStatus status) {
        this.status = status;
        return this;
    }

    public ProposalDetailsDTOBuilder withBidAmount(Double bidAmount) {
        this.bidAmount = bidAmount;
        return this;
    }

    public ProposalDetailsDTOBuilder withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    public ProposalDetailsDTOBuilder withMilestones(List<ProposalMilestoneDTO> milestones) {
        this.milestones = milestones;
        return this;
    }

    public ProposalDetailsDTOBuilder withTotalMilestones(int totalMilestones) {
        this.totalMilestones = totalMilestones;
        return this;
    }

    public ProposalDetailsDTOBuilder withCompletedMilestones(int completedMilestones) {
        this.completedMilestones = completedMilestones;
        return this;
    }

    public ProposalDetailsDTO build() {
        return new ProposalDetailsDTO(
                proposalId,
                jobId,
                freelancerId,
                status,
                bidAmount,
                metadata,
                milestones,
                totalMilestones,
                completedMilestones
        );
    }
}