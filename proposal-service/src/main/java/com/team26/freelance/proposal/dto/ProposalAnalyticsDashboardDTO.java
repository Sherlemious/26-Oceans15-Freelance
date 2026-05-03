package com.team26.freelance.proposal.dto;

import java.util.Map;

public class ProposalAnalyticsDashboardDTO {

    private long totalProposals;
    private double acceptanceRate;
    private double averageBidAmount;
    private double averageEstimatedDays;
    private Map<String, Long> proposalsByStatus;

    private ProposalAnalyticsDashboardDTO() {}

    public long getTotalProposals() { return totalProposals; }
    public double getAcceptanceRate() { return acceptanceRate; }
    public double getAverageBidAmount() { return averageBidAmount; }
    public double getAverageEstimatedDays() { return averageEstimatedDays; }
    public Map<String, Long> getProposalsByStatus() { return proposalsByStatus; }

    // ── Builder ────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private long totalProposals;
        private double acceptanceRate;
        private double averageBidAmount;
        private double averageEstimatedDays;
        private Map<String, Long> proposalsByStatus;

        public Builder withTotalProposals(long totalProposals) {
            this.totalProposals = totalProposals;
            return this;
        }

        public Builder withAcceptanceRate(double acceptanceRate) {
            this.acceptanceRate = acceptanceRate;
            return this;
        }

        public Builder withAverageBidAmount(double averageBidAmount) {
            this.averageBidAmount = averageBidAmount;
            return this;
        }

        public Builder withAverageEstimatedDays(double averageEstimatedDays) {
            this.averageEstimatedDays = averageEstimatedDays;
            return this;
        }

        public Builder withProposalsByStatus(Map<String, Long> proposalsByStatus) {
            this.proposalsByStatus = proposalsByStatus;
            return this;
        }

        public ProposalAnalyticsDashboardDTO build() {
            ProposalAnalyticsDashboardDTO dto = new ProposalAnalyticsDashboardDTO();
            dto.totalProposals = this.totalProposals;
            dto.acceptanceRate = this.acceptanceRate;
            dto.averageBidAmount = this.averageBidAmount;
            dto.averageEstimatedDays = this.averageEstimatedDays;
            dto.proposalsByStatus = this.proposalsByStatus;
            return dto;
        }
    }
}