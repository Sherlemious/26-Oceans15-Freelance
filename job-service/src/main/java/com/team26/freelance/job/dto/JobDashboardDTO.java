package com.team26.freelance.job.dto;

public class JobDashboardDTO {
    private final Long jobId;
    private final String title;
    private final Long totalProposals;
    private final Long acceptedProposals;
    private final Double averageBidAmount;
    private final Long activeAttachments;
    private final Double rating;

    private JobDashboardDTO(Builder builder) {
        this.jobId = builder.jobId;
        this.title = builder.title;
        this.totalProposals = builder.totalProposals;
        this.acceptedProposals = builder.acceptedProposals;
        this.averageBidAmount = builder.averageBidAmount;
        this.activeAttachments = builder.activeAttachments;
        this.rating = builder.rating;
    }

    public static class Builder {
        private Long jobId;
        private String title;
        private Long totalProposals;
        private Long acceptedProposals;
        private Double averageBidAmount;
        private Long activeAttachments;
        private Double rating;

        public Builder jobId(Long jobId) { this.jobId = jobId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder totalProposals(Long totalProposals) { this.totalProposals = totalProposals; return this; }
        public Builder acceptedProposals(Long acceptedProposals) { this.acceptedProposals = acceptedProposals; return this; }
        public Builder averageBidAmount(Double averageBidAmount) { this.averageBidAmount = averageBidAmount; return this; }
        public Builder activeAttachments(Long activeAttachments) { this.activeAttachments = activeAttachments; return this; }
        public Builder rating(Double rating) { this.rating = rating; return this; }

        public JobDashboardDTO build() {
            return new JobDashboardDTO(this);
        }
    }

    public Long getJobId() { return jobId; }
    public String getTitle() { return title; }
    public Long getTotalProposals() { return totalProposals; }
    public Long getAcceptedProposals() { return acceptedProposals; }
    public Double getAverageBidAmount() { return averageBidAmount; }
    public Long getActiveAttachments() { return activeAttachments; }
    public Double getRating() { return rating; }
}