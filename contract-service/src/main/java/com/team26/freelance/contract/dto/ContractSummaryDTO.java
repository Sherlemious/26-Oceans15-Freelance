package com.team26.freelance.contract.dto;

public class ContractSummaryDTO {
    private Long id;
    private Long contractId;
    private String freelancerName;
    private String jobTitle;
    private Double agreedAmount;
    private String status;
    private Long durationDays;

    public ContractSummaryDTO(Long contractId, String freelancerName, String jobTitle,
                              Double agreedAmount, String status, Long durationDays) {
        this.id = contractId;
        this.contractId = contractId;
        this.freelancerName = freelancerName;
        this.jobTitle = jobTitle;
        this.agreedAmount = agreedAmount;
        this.status = status;
        this.durationDays = durationDays;
    }

    // Private no-arg constructor for Builder
    private ContractSummaryDTO() {}

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long contractId;
        private String freelancerName;
        private String jobTitle;
        private Double agreedAmount;
        private String status;
        private Long durationDays;

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

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder durationDays(Long durationDays) {
            this.durationDays = durationDays;
            return this;
        }

        public ContractSummaryDTO build() {
            return new ContractSummaryDTO(contractId, freelancerName, jobTitle,
                    agreedAmount, status, durationDays);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getContractId() {
        return contractId;
    }

    public Long getId() {
        return id;
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

    public String getStatus() {
        return status;
    }

    public Long getDurationDays() {
        return durationDays;
    }
}
