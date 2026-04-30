package com.team26.freelance.job.dto;

public class JobProposalSummaryDTO {
    private Long jobId;
    private String title;
    private Long totalProposals;
    private Double averageBidAmount;
    private Double lowestBid;
    private Double highestBid;

    public JobProposalSummaryDTO() {}

    public JobProposalSummaryDTO(Long jobId, String title, Long totalProposals,
                                 Double averageBidAmount, Double lowestBid, Double highestBid) {
        this.jobId = jobId;
        this.title = title;
        this.totalProposals = totalProposals;
        this.averageBidAmount = averageBidAmount;
        this.lowestBid = lowestBid;
        this.highestBid = highestBid;
    }

    public Long getJobId() { return jobId; }
    public String getTitle() { return title; }
    public Long getTotalProposals() { return totalProposals; }
    public Double getAverageBidAmount() { return averageBidAmount; }
    public Double getLowestBid() { return lowestBid; }
    public Double getHighestBid() { return highestBid; }
}