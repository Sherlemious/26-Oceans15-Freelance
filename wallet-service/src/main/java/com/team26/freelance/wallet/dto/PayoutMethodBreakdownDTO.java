package com.team26.freelance.wallet.dto;

import java.math.BigDecimal;

public class PayoutMethodBreakdownDTO {

    private String payoutMethod;
    private BigDecimal totalAmount;
    private long count;
    private BigDecimal averageAmount;
    private double successRate;

    public PayoutMethodBreakdownDTO() {}

    public PayoutMethodBreakdownDTO(String payoutMethod, BigDecimal totalAmount,
                                    long count, BigDecimal averageAmount, double successRate) {
        this.payoutMethod = payoutMethod;
        this.totalAmount = totalAmount;
        this.count = count;
        this.averageAmount = averageAmount;
        this.successRate = successRate;
    }

    public String getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(String payoutMethod) { this.payoutMethod = payoutMethod; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
    public BigDecimal getAverageAmount() { return averageAmount; }
    public void setAverageAmount(BigDecimal averageAmount) { this.averageAmount = averageAmount; }
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
}
