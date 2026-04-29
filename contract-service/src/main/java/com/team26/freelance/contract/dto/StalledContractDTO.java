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

    // Private no-arg constructor for Builder
    private StalledContractDTO() {}

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long contractId;
        private String freelancerName;
        private String jobTitle;
        private Double agreedAmount;
        private Double progressPercentage;
        private Double daysSinceLastActivity;

        private Builder() {}

        public Builder contractId(Long contractId) {
            this.contractId = contractId;
            return this;
        }

        public Builder freelancerName(String freelancerName) {
            this.freelancerName = freelancerName;
            return this;
        }

        public Builder jobTitle(String jobTitle) {
            this.jobTitle = jobTitle;
            return this;
        }

        public Builder agreedAmount(Double agreedAmount) {
            this.agreedAmount = agreedAmount;
            return this;
        }

        public Builder progressPercentage(Double progressPercentage) {
            this.progressPercentage = progressPercentage;
            return this;
        }

        public Builder daysSinceLastActivity(Double daysSinceLastActivity) {
            this.daysSinceLastActivity = daysSinceLastActivity;
            return this;
        }

        public StalledContractDTO build() {
            return new StalledContractDTO(contractId, freelancerName, jobTitle,
                    agreedAmount, progressPercentage, daysSinceLastActivity);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

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
