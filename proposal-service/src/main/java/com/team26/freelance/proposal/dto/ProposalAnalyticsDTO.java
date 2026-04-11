package com.team26.freelance.proposal.dto;

public class ProposalAnalyticsDTO {
    private long totalProposals;
    private long acceptedProposals;
    private long rejectedProposals;
    private double totalBidValue;
    private double averageBid;
    private double acceptanceRate;

    public ProposalAnalyticsDTO(long totalProposals, long acceptedProposals, long rejectedProposals,
                                double totalBidValue, double averageBid, double acceptanceRate) {
        this.totalProposals = totalProposals;
        this.acceptedProposals = acceptedProposals;
        this.rejectedProposals = rejectedProposals;
        this.totalBidValue = totalBidValue;
        this.averageBid = averageBid;
        this.acceptanceRate = acceptanceRate;
    }

    public long getTotalProposals() { return totalProposals; }
    public long getAcceptedProposals() { return acceptedProposals; }
    public long getRejectedProposals() { return rejectedProposals; }
    public double getTotalBidValue() { return totalBidValue; }
    public double getAverageBid() { return averageBid; }
    public double getAcceptanceRate() { return acceptanceRate; }
}
