package com.team26.freelance.wallet.dto;

import java.math.BigDecimal;

public class PayoutMethodBreakdownDTO {

    private String method;
    private long successCount;
    private long failureCount;
    private BigDecimal totalAmount;
    private double successRate;

    public PayoutMethodBreakdownDTO() {}

    public PayoutMethodBreakdownDTO(String method, long successCount, long failureCount,
                                    BigDecimal totalAmount, double successRate) {
        this.method = method;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalAmount = totalAmount;
        this.successRate = successRate;
    }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    public long getFailureCount() { return failureCount; }
    public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
}
