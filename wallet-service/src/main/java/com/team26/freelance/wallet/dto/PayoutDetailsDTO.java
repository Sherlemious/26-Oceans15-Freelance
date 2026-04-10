package com.team26.freelance.wallet.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PayoutDetailsDTO {

    private Long payoutId;
    private Long contractId;
    private Long freelancerId;
    private Double originalAmount;
    private String method;
    private String status;
    private Map<String, Object> transactionDetails;
    private List<AppliedPromoCodeDTO> appliedPromoCodes = new ArrayList<>();
    private Double totalDiscount;
    private Double finalAmount;

    public PayoutDetailsDTO() {
    }

    public Long getPayoutId() {
        return payoutId;
    }

    public void setPayoutId(Long payoutId) {
        this.payoutId = payoutId;
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

    public Double getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(Double originalAmount) {
        this.originalAmount = originalAmount;
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

    public List<AppliedPromoCodeDTO> getAppliedPromoCodes() {
        return appliedPromoCodes;
    }

    public void setAppliedPromoCodes(List<AppliedPromoCodeDTO> appliedPromoCodes) {
        this.appliedPromoCodes = appliedPromoCodes;
    }

    public Double getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(Double totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public Double getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(Double finalAmount) {
        this.finalAmount = finalAmount;
    }
}