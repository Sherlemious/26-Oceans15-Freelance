package com.team26.freelance.proposal.dto;

public class JobRecommendationDTO {

    private final Long jobId;
    private final String title;
    private final String category;
    private final long score;

    public JobRecommendationDTO(Long jobId, String title, String category, long score) {
        this.jobId = jobId;
        this.title = title;
        this.category = category;
        this.score = score;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public long getScore() {
        return score;
    }
}

