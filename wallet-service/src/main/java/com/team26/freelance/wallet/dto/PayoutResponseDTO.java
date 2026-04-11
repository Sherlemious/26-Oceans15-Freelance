package com.team26.freelance.wallet.dto;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PayoutResponseDTO {

    private Long id;
    private Long contractId;
    private Long freelancerId;
    private Double amount;
    private PayoutMethod method;
    private PayoutStatus status;
    private Map<String, Object> transactionDetails;
    private LocalDateTime createdAt;
    private List<PayoutPromoResponseDTO> promos;

    public PayoutResponseDTO(Payout payout) {
        this.id = payout.getId();
        this.contractId = payout.getContractId();
        this.freelancerId = payout.getFreelancerId();
        this.amount = payout.getAmount();
        this.method = payout.getMethod();
        this.status = payout.getStatus();
        this.transactionDetails = payout.getTransactionDetails();
        this.createdAt = payout.getCreatedAt();
        this.promos = payout.getPayoutPromos().stream()
                .map(PayoutPromoResponseDTO::new)
                .toList();
    }

    public Long getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    public Long getFreelancerId() {
        return freelancerId;
    }

    public Double getAmount() {
        return amount;
    }

    public PayoutMethod getMethod() {
        return method;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public Map<String, Object> getTransactionDetails() {
        return transactionDetails;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<PayoutPromoResponseDTO> getPromos() {
        return promos;
    }
}
