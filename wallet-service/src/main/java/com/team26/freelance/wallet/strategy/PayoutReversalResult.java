package com.team26.freelance.wallet.strategy;

public class PayoutReversalResult {

    private final double amountReturned;
    private final String strategyApplied;
    private final String reason;
    private final boolean approved;

    public PayoutReversalResult(double amountReturned, String strategyApplied,
                                String reason, boolean approved) {
        this.amountReturned = amountReturned;
        this.strategyApplied = strategyApplied;
        this.reason = reason;
        this.approved = approved;
    }

    public double getAmountReturned() { return amountReturned; }
    public String getStrategyApplied() { return strategyApplied; }
    public String getReason() { return reason; }
    public boolean isApproved() { return approved; }
}
