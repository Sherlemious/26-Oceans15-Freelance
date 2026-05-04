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

    private RevenueReportDTO(Builder builder) {
        this.totalRevenue = builder.totalRevenue;
        this.totalTransactions = builder.totalTransactions;
        this.averagePayout = builder.averagePayout;
        this.refundedAmount = builder.refundedAmount;
        this.refundCount = builder.refundCount;
    }

    public static Builder builder() {
        return new Builder();
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

    public static class Builder {
        private BigDecimal totalRevenue;
        private long totalTransactions;
        private BigDecimal averagePayout;
        private BigDecimal refundedAmount;
        private long refundCount;

        public Builder totalRevenue(BigDecimal totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder totalTransactions(long totalTransactions) {
            this.totalTransactions = totalTransactions;
            return this;
        }

        public Builder averagePayout(BigDecimal averagePayout) {
            this.averagePayout = averagePayout;
            return this;
        }

        public Builder refundedAmount(BigDecimal refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        public Builder refundCount(long refundCount) {
            this.refundCount = refundCount;
            return this;
        }

        public RevenueReportDTO build() {
            return new RevenueReportDTO(this);
        }
    }
}