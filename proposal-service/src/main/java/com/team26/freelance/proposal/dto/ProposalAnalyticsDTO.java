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

        private long totalProposals;
        private long acceptedProposals;
        private long rejectedProposals;
        private double totalBidValue;
        private double averageBid;
        private double acceptanceRate;

        public Builder withTotalProposals(long totalProposals) {
            this.totalProposals = totalProposals;
            return this;
        }

        public Builder withAcceptedProposals(long acceptedProposals) {
            this.acceptedProposals = acceptedProposals;
            return this;
        }

        public Builder withRejectedProposals(long rejectedProposals) {
            this.rejectedProposals = rejectedProposals;
            return this;
        }

        public Builder withTotalBidValue(double totalBidValue) {
            this.totalBidValue = totalBidValue;
            return this;
        }

        public Builder withAverageBid(double averageBid) {
            this.averageBid = averageBid;
            return this;
        }

        public Builder withAcceptanceRate(double acceptanceRate) {
            this.acceptanceRate = acceptanceRate;
            return this;
        }

        public ProposalAnalyticsDTO build() {
            ProposalAnalyticsDTO dto = new ProposalAnalyticsDTO();
            dto.totalProposals = this.totalProposals;
            dto.acceptedProposals = this.acceptedProposals;
            dto.rejectedProposals = this.rejectedProposals;
            dto.totalBidValue = this.totalBidValue;
            dto.averageBid = this.averageBid;
            dto.acceptanceRate = this.acceptanceRate;
            return dto;
        }
    }
}