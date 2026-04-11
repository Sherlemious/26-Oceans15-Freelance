package com.team26.freelance.wallet.dto;

import java.math.BigDecimal;

public class RevenueReportDTO {

    private BigDecimal totalRevenue;
    private long totalTransactions;
    private BigDecimal averagePayout;
    private BigDecimal refundedAmount;
    private long refundCount;

    public RevenueReportDTO() {
    }

    public RevenueReportDTO(BigDecimal totalRevenue,
                            long totalTransactions,
                            BigDecimal averagePayout,
                            BigDecimal refundedAmount,
                            long refundCount) {
        this.totalRevenue = totalRevenue;
        this.totalTransactions = totalTransactions;
        this.averagePayout = averagePayout;
        this.refundedAmount = refundedAmount;
        this.refundCount = refundCount;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public BigDecimal getAveragePayout() {
        return averagePayout;
    }

    public void setAveragePayout(BigDecimal averagePayout) {
        this.averagePayout = averagePayout;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public long getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(long refundCount) {
        this.refundCount = refundCount;
    }
}
