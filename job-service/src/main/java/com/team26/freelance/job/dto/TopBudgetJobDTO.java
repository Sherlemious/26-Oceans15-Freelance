package com.team26.freelance.job.dto;

public class TopBudgetJobDTO {
    private final Long jobId;
    private final String title;
    private final Double budgetMax;
    private final Long totalProposals;

    private TopBudgetJobDTO(Builder builder) {
        this.jobId = builder.jobId;
        this.title = builder.title;
        this.budgetMax = builder.budgetMax;
        this.totalProposals = builder.totalProposals;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long jobId;
        private String title;
        private Double budgetMax;
        private Long totalProposals;

        public Builder jobId(Long jobId) { this.jobId = jobId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder budgetMax(Double budgetMax) { this.budgetMax = budgetMax; return this; }
        public Builder totalProposals(Long totalProposals) { this.totalProposals = totalProposals; return this; }

        public TopBudgetJobDTO build() {
            return new TopBudgetJobDTO(this);
        }
    }

    public Long getJobId() { return jobId; }
    public String getTitle() { return title; }
    public Double getBudgetMax() { return budgetMax; }
    public Long getTotalProposals() { return totalProposals; }
}