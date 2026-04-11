package com.team26.freelance.contract.service;

import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ContractPurgeService {

    private final ContractRepository contractRepository;

    public ContractPurgeService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Transactional
    public long purgeOldContracts(int olderThanDays) {
        if (olderThanDays < 1) {
            throw new IllegalArgumentException("olderThanDays must be >= 1");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        return contractRepository.deleteOldContracts(cutoff);
    }
}
