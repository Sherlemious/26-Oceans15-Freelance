package com.team26.freelance.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProposalDTO {
    private Long id;
    private Long jobId;
    private Long freelancerId;
    private String status;
    private BigDecimal bidAmount;
    private LocalDateTime acceptedAt;

    public ProposalDTO() {
    }

    public ProposalDTO(Long id,
                       Long jobId,
                       Long freelancerId,
                       String status,
                       BigDecimal bidAmount,
                       LocalDateTime acceptedAt) {
        this.id = id;
        this.jobId = jobId;
        this.freelancerId = freelancerId;
        this.status = status;
        this.bidAmount = bidAmount;
        this.acceptedAt = acceptedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getFreelancerId() {
        return freelancerId;
    }

    public void setFreelancerId(Long freelancerId) {
        this.freelancerId = freelancerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(BigDecimal bidAmount) {
        this.bidAmount = bidAmount;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
