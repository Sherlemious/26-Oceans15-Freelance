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

        private final ProposalAnalyticsDashboardDTO dto = new ProposalAnalyticsDashboardDTO();

        public Builder withTotalProposals(long totalProposals) {
            dto.totalProposals = totalProposals;
            return this;
        }

        public Builder withAcceptanceRate(double acceptanceRate) {
            dto.acceptanceRate = acceptanceRate;
            return this;
        }

        public Builder withAverageBidAmount(double averageBidAmount) {
            dto.averageBidAmount = averageBidAmount;
            return this;
        }

        public Builder withAverageEstimatedDays(double averageEstimatedDays) {
            dto.averageEstimatedDays = averageEstimatedDays;
            return this;
        }

        public Builder withProposalsByStatus(Map<String, Long> proposalsByStatus) {
            dto.proposalsByStatus = proposalsByStatus;
            return this;
        }

        public ProposalAnalyticsDashboardDTO build() {
            return dto;
        }
    }
}