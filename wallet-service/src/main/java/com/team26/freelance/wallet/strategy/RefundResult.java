package com.team26.freelance.wallet.strategy;

public class RefundResult {

    private final double amount;
    private final String reasonCode;
    private final String strategyName;
    private final boolean approved;

    public RefundResult(double amount, String reasonCode, String strategyName, boolean approved) {
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.strategyName = strategyName;
        this.approved = approved;
    }

    public double getAmount() { return amount; }
    public String getReasonCode() { return reasonCode; }
    public String getStrategyName() { return strategyName; }
    public boolean isApproved() { return approved; }
}
