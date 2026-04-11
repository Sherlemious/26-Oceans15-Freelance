package com.team26.freelance.wallet.dto;

import java.math.BigDecimal;

public interface RevenueReportProjection {
    BigDecimal getTotalRevenue();

    Long getTotalTransactions();

    BigDecimal getRefundedAmount();

    Long getRefundCount();
}
