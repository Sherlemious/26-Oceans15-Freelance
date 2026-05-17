package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.CategoryRevenueDTO;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.JobDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformFeeAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(PlatformFeeAnalyticsService.class);
    private static final String UNKNOWN_CATEGORY = "UNKNOWN";
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private final PayoutRepository payoutRepository;
    private final WalletReadClientService walletReadClientService;

    public PlatformFeeAnalyticsService(PayoutRepository payoutRepository,
                                       WalletReadClientService walletReadClientService) {
        this.payoutRepository = payoutRepository;
        this.walletReadClientService = walletReadClientService;
    }

    public List<CategoryRevenueDTO> getPlatformFeeAnalytics(LocalDate startDate, LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be before or equal to endDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        List<Payout> payouts = payoutRepository.findByStatusAndCreatedAtRange(
                PayoutStatus.COMPLETED,
                start,
                endExclusive
        );

        Map<Long, Long> contractToJobCache = new HashMap<>();
        Map<Long, String> jobToCategoryCache = new HashMap<>();
        Map<String, AggregateAccumulator> aggregatesByCategory = new HashMap<>();

        for (Payout payout : payouts) {
            String category = resolveCategory(payout.getContractId(), contractToJobCache, jobToCategoryCache);
            BigDecimal totalRevenue = toBigDecimal(payout.getAmount());
            BigDecimal platformFeeRevenue = extractPlatformFee(payout.getTransactionDetails(), totalRevenue);
            BigDecimal netPayoutRevenue = totalRevenue.subtract(platformFeeRevenue);

            AggregateAccumulator aggregate = aggregatesByCategory.computeIfAbsent(
                    category,
                    ignored -> new AggregateAccumulator()
            );
            aggregate.totalRevenue = aggregate.totalRevenue.add(totalRevenue);
            aggregate.platformFeeRevenue = aggregate.platformFeeRevenue.add(platformFeeRevenue);
            aggregate.netPayoutRevenue = aggregate.netPayoutRevenue.add(netPayoutRevenue);
            aggregate.payoutCount += 1;
        }

        List<CategoryRevenueDTO> result = new ArrayList<>();
        for (Map.Entry<String, AggregateAccumulator> entry : aggregatesByCategory.entrySet()) {
            AggregateAccumulator aggregate = entry.getValue();
            CategoryRevenueDTO dto = CategoryRevenueDTO.builder()
                    .category(entry.getKey())
                    .totalRevenue(aggregate.totalRevenue.doubleValue())
                    .platformFeeRevenue(aggregate.platformFeeRevenue.doubleValue())
                    .netPayoutRevenue(aggregate.netPayoutRevenue.doubleValue())
                    .payoutCount(aggregate.payoutCount)
                    .build();
            result.add(dto);
        }

        result.sort(Comparator.comparing(CategoryRevenueDTO::getTotalRevenue).reversed());

        return result;
    }

    private String resolveCategory(Long contractId,
                                   Map<Long, Long> contractToJobCache,
                                   Map<Long, String> jobToCategoryCache) {
        try {
            Long jobId = contractToJobCache.computeIfAbsent(contractId, this::resolveJobIdFromContract);
            if (jobId == null) {
                return UNKNOWN_CATEGORY;
            }
            return jobToCategoryCache.computeIfAbsent(jobId, this::resolveCategoryFromJob);
        } catch (RuntimeException ex) {
            log.warn("Failed resolving category for contractId={}: {}", contractId, ex.getMessage());
            return UNKNOWN_CATEGORY;
        }
    }

    private Long resolveJobIdFromContract(Long contractId) {
        ContractDTO contract = walletReadClientService.getContract(contractId);
        return contract == null ? null : contract.getJobId();
    }

    private String resolveCategoryFromJob(Long jobId) {
        JobDTO job = walletReadClientService.getJob(jobId);
        if (job == null || job.getCategory() == null || job.getCategory().isBlank()) {
            return UNKNOWN_CATEGORY;
        }
        return job.getCategory();
    }

    private BigDecimal extractPlatformFee(Map<String, Object> transactionDetails, BigDecimal amount) {
        if (transactionDetails == null) {
            return amount.multiply(PLATFORM_FEE_RATE);
        }
        Object rawPlatformFee = transactionDetails.get("platformFee");
        if (rawPlatformFee == null) {
            return amount.multiply(PLATFORM_FEE_RATE);
        }
        if (rawPlatformFee instanceof Number number) {
            return toBigDecimal(number.doubleValue());
        }
        if (rawPlatformFee instanceof String value && !value.isBlank()) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ex) {
                log.warn("Invalid platformFee format '{}', using fallback", value);
                return amount.multiply(PLATFORM_FEE_RATE);
            }
        }
        return amount.multiply(PLATFORM_FEE_RATE);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private static class AggregateAccumulator {
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal platformFeeRevenue = BigDecimal.ZERO;
        private BigDecimal netPayoutRevenue = BigDecimal.ZERO;
        private long payoutCount = 0L;
    }
}
