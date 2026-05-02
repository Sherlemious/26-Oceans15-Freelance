package com.team26.freelance.job.dto;

public class JobProposalSummaryDTO {
    private final Long jobId;
    private final String title;
    private final Long totalProposals;
    private final Double averageBidAmount;
    private final Double lowestBid;
    private final Double highestBid;

    private JobProposalSummaryDTO(Builder builder) {
        this.jobId = builder.jobId;
        this.title = builder.title;
        this.totalProposals = builder.totalProposals;
        this.averageBidAmount = builder.averageBidAmount;
        this.lowestBid = builder.lowestBid;
        this.highestBid = builder.highestBid;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long jobId;
        private String title;
        private Long totalProposals;
        private Double averageBidAmount;
        private Double lowestBid;
        private Double highestBid;

        public Builder jobId(Long jobId) { this.jobId = jobId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder totalProposals(Long totalProposals) { this.totalProposals = totalProposals; return this; }
        public Builder averageBidAmount(Double averageBidAmount) { this.averageBidAmount = averageBidAmount; return this; }
        public Builder lowestBid(Double lowestBid) { this.lowestBid = lowestBid; return this; }
        public Builder highestBid(Double highestBid) { this.highestBid = highestBid; return this; }

        public JobProposalSummaryDTO build() {
            return new JobProposalSummaryDTO(this);
        }
    }

    public Long getJobId() { return jobId; }
    public String getTitle() { return title; }
    public Long getTotalProposals() { return totalProposals; }
    public Double getAverageBidAmount() { return averageBidAmount; }
    public Double getLowestBid() { return lowestBid; }
    public Double getHighestBid() { return highestBid; }
}