package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.CategoryRevenueDTO;
import com.team26.freelance.wallet.dto.CategoryRevenueProjection;
import com.team26.freelance.wallet.repository.PayoutRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformFeeAnalyticsService {

    private final PayoutRepository payoutRepository;

    public PlatformFeeAnalyticsService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    public List<CategoryRevenueDTO> getPlatformFeeAnalyticsAllTime() {
        List<CategoryRevenueProjection> rows =
                payoutRepository.getPlatformFeeAnalyticsByCategoryAllTime();

        List<CategoryRevenueDTO> result = new ArrayList<>();

        for (CategoryRevenueProjection row : rows) {
            CategoryRevenueDTO dto = new CategoryRevenueDTO.Builder()
                    .jobCategory(row.getJobCategory())
                    .totalFees(row.getTotalFees())
                    .averageFee(row.getAverageFee())
                    .payoutCount(row.getPayoutCount() == null ? 0L : row.getPayoutCount())
                    .build();
            result.add(dto);
        }

        return result;
    }
}