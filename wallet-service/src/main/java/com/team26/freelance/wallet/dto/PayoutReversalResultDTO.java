package com.team26.freelance.wallet.dto;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.strategy.RefundResult;

import java.util.Map;

public class PayoutReversalResultDTO {

    private Long payoutId;
    private Double originalAmount;
    private Double amountReturned;
    private String strategyApplied;
    private String reason;
    private String payoutStatus;
    private Map<String, Object> transactionDetails;

    public PayoutReversalResultDTO(Payout payout, RefundResult result) {
        this.payoutId = payout.getId();
        this.originalAmount = payout.getAmount();
        this.amountReturned = result.getAmount();
        this.strategyApplied = result.getStrategyName();
        this.reason = result.getReasonCode();
        this.payoutStatus = payout.getStatus().name();
        this.transactionDetails = payout.getTransactionDetails();
    }

    public Long getPayoutId() { return payoutId; }
    public Double getOriginalAmount() { return originalAmount; }
    public Double getAmountReturned() { return amountReturned; }
    public String getStrategyApplied() { return strategyApplied; }
    public String getReason() { return reason; }
    public String getPayoutStatus() { return payoutStatus; }
    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
}
