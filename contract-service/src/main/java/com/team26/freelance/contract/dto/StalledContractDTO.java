package com.team26.freelance.contract.dto;

public class StalledContractDTO {
    private Long contractId;
    private String freelancerName;
    private String jobTitle;
    private Double agreedAmount;
    private Double progressPercentage;
    private Double daysSinceLastActivity;

    public StalledContractDTO(Long contractId, String freelancerName, String jobTitle,
                              Double agreedAmount, Double progressPercentage, Double daysSinceLastActivity) {
        this.contractId = contractId;
        this.freelancerName = freelancerName;
        this.jobTitle = jobTitle;
        this.agreedAmount = agreedAmount;
        this.progressPercentage = progressPercentage;
        this.daysSinceLastActivity = daysSinceLastActivity;
    }

    public Long getContractId() {
        return contractId;
    }

    public String getFreelancerName() {
        return freelancerName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public Double getAgreedAmount() {
        return agreedAmount;
    }

    public Double getProgressPercentage() {
        return progressPercentage;
    }

    public Double getDaysSinceLastActivity() {
        return daysSinceLastActivity;
    }
}

