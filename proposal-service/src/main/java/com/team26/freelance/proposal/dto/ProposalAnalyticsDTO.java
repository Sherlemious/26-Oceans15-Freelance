package com.team26.freelance.proposal.dto;

public class ProposalAnalyticsDTO {
    private long totalProposals;
    private long acceptedProposals;
    private long rejectedProposals;
    private double totalBidValue;
    private double averageBid;
    private double acceptanceRate;

    private ProposalAnalyticsDTO() {}

    public long getTotalProposals() { return totalProposals; }
    public long getAcceptedProposals() { return acceptedProposals; }
    public long getRejectedProposals() { return rejectedProposals; }
    public double getTotalBidValue() { return totalBidValue; }
    public double getAverageBid() { return averageBid; }
    public double getAcceptanceRate() { return acceptanceRate; }

    // ── Builder ────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final ProposalAnalyticsDTO dto = new ProposalAnalyticsDTO();

        public Builder withTotalProposals(long totalProposals) {
            dto.totalProposals = totalProposals;
            return this;
        }

        public Builder withAcceptedProposals(long acceptedProposals) {
            dto.acceptedProposals = acceptedProposals;
            return this;
        }

        public Builder withRejectedProposals(long rejectedProposals) {
            dto.rejectedProposals = rejectedProposals;
            return this;
        }

        public Builder withTotalBidValue(double totalBidValue) {
            dto.totalBidValue = totalBidValue;
            return this;
        }

        public Builder withAverageBid(double averageBid) {
            dto.averageBid = averageBid;
            return this;
        }

        public Builder withAcceptanceRate(double acceptanceRate) {
            dto.acceptanceRate = acceptanceRate;
            return this;
        }

        public ProposalAnalyticsDTO build() {
            return dto;
        }
    }
}