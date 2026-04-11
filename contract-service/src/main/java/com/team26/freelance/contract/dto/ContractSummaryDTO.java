package com.team26.freelance.contract.dto;

public class ContractSummaryDTO {
    private Long contractId;
    private String freelancerName;
    private String jobTitle;
    private Double agreedAmount;
    private String status;
    private Long durationDays;

    public ContractSummaryDTO(Long contractId, String freelancerName, String jobTitle,
                              Double agreedAmount, String status, Long durationDays) {
        this.contractId = contractId;
        this.freelancerName = freelancerName;
        this.jobTitle = jobTitle;
        this.agreedAmount = agreedAmount;
        this.status = status;
        this.durationDays = durationDays;
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

    public String getStatus() {
        return status;
    }

    public Long getDurationDays() {
        return durationDays;
    }
}
