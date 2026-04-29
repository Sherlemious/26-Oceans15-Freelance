package com.team26.freelance.contract.service;

import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ContractPurgeService {

    private final ContractRepository contractRepository;
    private final ContractAnalyticsCacheService contractAnalyticsCacheService;

    public ContractPurgeService(ContractRepository contractRepository,
            ContractAnalyticsCacheService contractAnalyticsCacheService) {
        this.contractRepository = contractRepository;
        this.contractAnalyticsCacheService = contractAnalyticsCacheService;
    }

    @Transactional
    public long purgeOldContracts(int olderThanDays) {
        if (olderThanDays < 0) {
            throw new IllegalArgumentException("olderThanDays must be a positive number");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        long deleted = contractRepository.deleteOldContracts(cutoff);
        if (deleted > 0) {
            contractAnalyticsCacheService.evictDashboard();
        }
        return deleted;
    }
}
