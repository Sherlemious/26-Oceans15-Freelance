package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.RevenueReportDTO;
import com.team26.freelance.wallet.dto.RevenueReportProjection;
import com.team26.freelance.wallet.repository.PayoutRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class PayoutReportService {

    private final PayoutRepository payoutRepository;

    public PayoutReportService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    @Cacheable(
            cacheNames = "wallet-service::S5-F6",
            key = "T(java.util.Objects).hash(#startDate, #endDate)"
    )
    public RevenueReportDTO getRevenueReport(LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.of(1970, 1, 1);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }

        LocalDateTime start = effectiveStart.atStartOfDay();
        LocalDateTime endExclusive = effectiveEnd.plusDays(1).atStartOfDay();

        RevenueReportProjection report = payoutRepository.getRevenueReport(start, endExclusive);

        BigDecimal totalRevenue = report.getTotalRevenue();
        long totalTransactions = report.getTotalTransactions();
        BigDecimal refundedAmount = report.getRefundedAmount();
        long refundCount = report.getRefundCount();
        BigDecimal averagePayout = totalTransactions == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalTransactions), MathContext.DECIMAL64);

        return new RevenueReportDTO(totalRevenue, totalTransactions, averagePayout, refundedAmount, refundCount);
    }
}
