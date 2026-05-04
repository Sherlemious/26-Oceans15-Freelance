package com.team26.freelance.contract.service;

import com.team26.freelance.common.event.ObservabilityAction;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.observer.ContractEventSubject;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContractPurgeService {
    private final ContractRepository contractRepository;
    private final ContractCacheEvictionService cacheEvictionService;
    private final ContractEventSubject contractEventSubject;

    public ContractPurgeService(ContractRepository contractRepository,
                                ContractCacheEvictionService cacheEvictionService,
                                ContractEventSubject contractEventSubject) {
        this.contractRepository = contractRepository;
        this.cacheEvictionService = cacheEvictionService;
        this.contractEventSubject = contractEventSubject;
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
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("olderThanDays", olderThanDays);
        details.put("deletedCount", deleted);
        details.put("contractIds", purgeableIds);
        contractEventSubject.notifyObservers(ObservabilityAction.OLD_DATA_PURGED.name(), Map.of("details", details));
        return deleted;
    }
}
