package com.team26.freelance.contract.service;

import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContractPurgeService {

    private final ContractRepository contractRepository;
    private final ContractCacheEvictionService cacheEvictionService;

    public ContractPurgeService(ContractRepository contractRepository,
                                ContractCacheEvictionService cacheEvictionService) {
        this.contractRepository = contractRepository;
        this.cacheEvictionService = cacheEvictionService;
    }

    @Transactional
    public long purgeOldContracts(int olderThanDays) {
        if (olderThanDays < 0) {
            throw new IllegalArgumentException("olderThanDays must be a positive number");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        List<Long> purgeableIds = contractRepository.findPurgeableIds(
                cutoff, List.of(ContractStatus.COMPLETED, ContractStatus.TERMINATED));
        int deleted = contractRepository.deleteOldContracts(cutoff);
        if (deleted > 0) {
            cacheEvictionService.evictAfterContractsMutated(purgeableIds);
        }
        return deleted;
    }
}
