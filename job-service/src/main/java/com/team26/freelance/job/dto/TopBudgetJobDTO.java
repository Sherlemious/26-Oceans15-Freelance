package com.team26.freelance.job.dto;

public class TopBudgetJobDTO {
    private Long jobId;
    private String title;
    private Double budgetMax;
    private Long totalProposals;

    public TopBudgetJobDTO(Long jobId, String title, Double budgetMax, Long totalProposals) {
        this.jobId = jobId;
        this.title = title;
        this.budgetMax = budgetMax;
        this.totalProposals = totalProposals;
    }

    public Long getJobId() { return jobId; }
    public String getTitle() { return title; }
    public Double getBudgetMax() { return budgetMax; }
    public Long getTotalProposals() { return totalProposals; }
}
