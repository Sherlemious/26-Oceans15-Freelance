package com.team26.freelance.wallet.dto;

import java.math.BigDecimal;

public interface CategoryRevenueProjection {
    String getCategory();
    BigDecimal getPlatformFeeRevenue();
    BigDecimal getTotalRevenue();
    Long getPayoutCount();
}