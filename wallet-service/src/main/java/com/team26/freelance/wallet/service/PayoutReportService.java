package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.RevenueReportDTO;
import com.team26.freelance.wallet.repository.PayoutRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class PayoutReportService {

    private final PayoutRepository payoutRepository;

    public PayoutReportService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    public RevenueReportDTO getRevenueReport(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        Object[] row = payoutRepository.getRevenueReport(start, end);
        double totalRevenue = ((Number) row[0]).doubleValue();
        long totalTransactions = ((Number) row[1]).longValue();
        double refundedAmount = ((Number) row[2]).doubleValue();
        long refundCount = ((Number) row[3]).longValue();
        double averagePayout = totalTransactions == 0 ? 0 : totalRevenue / totalTransactions;

        return new RevenueReportDTO(totalRevenue, totalTransactions, averagePayout, refundedAmount, refundCount);
    }
}
