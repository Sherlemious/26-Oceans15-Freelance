package com.team26.freelance.contracts.dto;

import java.math.BigDecimal;

public class JobProposalSummaryDTO {
    private Long totalProposals;
    private Long acceptedProposals;
    private BigDecimal averageBidAmount;
    private BigDecimal lowestBid;
    private BigDecimal highestBid;

    public JobProposalSummaryDTO() {
    }

    public JobProposalSummaryDTO(Long totalProposals,
                                 Long acceptedProposals,
                                 BigDecimal averageBidAmount,
                                 BigDecimal lowestBid,
                                 BigDecimal highestBid) {
        this.totalProposals = totalProposals;
        this.acceptedProposals = acceptedProposals;
        this.averageBidAmount = averageBidAmount;
        this.lowestBid = lowestBid;
        this.highestBid = highestBid;
    }

    public Long getTotalProposals() {
        return totalProposals;
    }

    public void setTotalProposals(Long totalProposals) {
        this.totalProposals = totalProposals;
    }

    public Long getAcceptedProposals() {
        return acceptedProposals;
    }

    public void setAcceptedProposals(Long acceptedProposals) {
        this.acceptedProposals = acceptedProposals;
    }

    public BigDecimal getAverageBidAmount() {
        return averageBidAmount;
    }

    public void setAverageBidAmount(BigDecimal averageBidAmount) {
        this.averageBidAmount = averageBidAmount;
    }

    public BigDecimal getLowestBid() {
        return lowestBid;
    }

    public void setLowestBid(BigDecimal lowestBid) {
        this.lowestBid = lowestBid;
    }

    public BigDecimal getHighestBid() {
        return highestBid;
    }

    public void setHighestBid(BigDecimal highestBid) {
        this.highestBid = highestBid;
    }
}
