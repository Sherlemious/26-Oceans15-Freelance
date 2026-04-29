package com.team26.freelance.wallet.dto;

public interface CategoryRevenueProjection {
    String getJobCategory();
    Double getTotalFees();
    Double getAverageFee();
    Long getPayoutCount();
}