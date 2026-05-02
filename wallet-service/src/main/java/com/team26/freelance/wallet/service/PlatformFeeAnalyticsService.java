package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.CategoryRevenueDTO;
import com.team26.freelance.wallet.dto.CategoryRevenueProjection;
import com.team26.freelance.wallet.repository.PayoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.cache.annotation.Cacheable;

@Service
public class PlatformFeeAnalyticsService {

    private final PayoutRepository payoutRepository;

    public PlatformFeeAnalyticsService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }
    @Cacheable(
            cacheNames = "wallet-service::S5-F10",
            key = "#startDate + ':' + #endDate"
    )
    public List<CategoryRevenueDTO> getPlatformFeeAnalytics(LocalDate startDate, LocalDate endDate) {
        

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be before or equal to endDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        List<CategoryRevenueProjection> rows =
                payoutRepository.getCategoryRevenueAnalytics(start, endExclusive);

        List<CategoryRevenueDTO> result = new ArrayList<>();

        for (CategoryRevenueProjection row : rows) {
            BigDecimal platformFeeRevenue = row.getPlatformFeeRevenue() == null
                    ? BigDecimal.ZERO
                    : row.getPlatformFeeRevenue();

            BigDecimal totalRevenue = row.getTotalRevenue() == null
                    ? BigDecimal.ZERO
                    : row.getTotalRevenue();

            BigDecimal netPayoutRevenue = totalRevenue.subtract(platformFeeRevenue);

            CategoryRevenueDTO dto = CategoryRevenueDTO.builder()
                    .category(row.getCategory())
                    .platformFeeRevenue(platformFeeRevenue.doubleValue())
                    .totalRevenue(totalRevenue.doubleValue())
                    .netPayoutRevenue(netPayoutRevenue.doubleValue())
                    .payoutCount(row.getPayoutCount() == null ? 0L : row.getPayoutCount())
                    .build();

            result.add(dto);
        }

        return result;
    }
}