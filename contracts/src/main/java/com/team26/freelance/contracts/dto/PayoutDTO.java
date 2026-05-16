package com.team26.freelance.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public class PayoutDTO {
    private Long id;
    private Long contractId;
    private Long freelancerId;
    private BigDecimal amount;
    private String method;
    private String status;
    private Map<String, Object> transactionDetails;
    private LocalDateTime createdAt;

    public PayoutDTO() {
    }

    public PayoutDTO(Long id,
                     Long contractId,
                     Long freelancerId,
                     BigDecimal amount,
                     String method,
                     String status,
                     Map<String, Object> transactionDetails,
                     LocalDateTime createdAt) {
        this.id = id;
        this.contractId = contractId;
        this.freelancerId = freelancerId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.transactionDetails = transactionDetails;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Long getFreelancerId() {
        return freelancerId;
    }

    public void setFreelancerId(Long freelancerId) {
        this.freelancerId = freelancerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getTransactionDetails() {
        return transactionDetails;
    }

    public void setTransactionDetails(Map<String, Object> transactionDetails) {
        this.transactionDetails = transactionDetails;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
