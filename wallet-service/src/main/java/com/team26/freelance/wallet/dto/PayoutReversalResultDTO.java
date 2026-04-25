package com.team26.freelance.wallet.dto;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.strategy.PayoutReversalResult;

public class PayoutReversalResultDTO {

    private Long payoutId;
    private Double originalAmount;
    private Double amountReturned;
    private String strategyApplied;
    private String reason;
    private String payoutStatus;

    public PayoutReversalResultDTO(Payout payout, PayoutReversalResult result) {
        this.payoutId = payout.getId();
        this.originalAmount = payout.getAmount();
        this.amountReturned = result.getAmountReturned();
        this.strategyApplied = result.getStrategyApplied();
        this.reason = result.getReason();
        this.payoutStatus = payout.getStatus().name();
    }

    public Long getPayoutId() { return payoutId; }
    public Double getOriginalAmount() { return originalAmount; }
    public Double getAmountReturned() { return amountReturned; }
    public String getStrategyApplied() { return strategyApplied; }
    public String getReason() { return reason; }
    public String getPayoutStatus() { return payoutStatus; }
}
