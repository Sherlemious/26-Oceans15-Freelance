package com.team26.freelance.wallet.dto;

public class RevenueReportDTO {

    private double totalRevenue;
    private long totalTransactions;
    private double averagePayout;
    private double refundedAmount;
    private long refundCount;

    public RevenueReportDTO() {
    }

    public RevenueReportDTO(double totalRevenue,
                            long totalTransactions,
                            double averagePayout,
                            double refundedAmount,
                            long refundCount) {
        this.totalRevenue = totalRevenue;
        this.totalTransactions = totalTransactions;
        this.averagePayout = averagePayout;
        this.refundedAmount = refundedAmount;
        this.refundCount = refundCount;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public double getAveragePayout() {
        return averagePayout;
    }

    public void setAveragePayout(double averagePayout) {
        this.averagePayout = averagePayout;
    }

    public double getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(double refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public long getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(long refundCount) {
        this.refundCount = refundCount;
    }
}
