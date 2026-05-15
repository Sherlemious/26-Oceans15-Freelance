package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.FreelancerPerformanceDTO;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.repository.FreelancerPerformanceProjection;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

import java.time.LocalDateTime;

@Service
public class FreelancerPerformanceService {

    private final ContractRepository contractRepository;
    private final ContractReadClientService contractReadClientService;

    public FreelancerPerformanceService(ContractRepository contractRepository,
                                        ContractReadClientService contractReadClientService) {
        this.contractRepository = contractRepository;
        this.contractReadClientService = contractReadClientService;
    }

    @Cacheable(value = "contract-s4-f8", key = "@contractCacheKeys.featureKeyWithId('S4-F8', #freelancerId, #startDateParam, #endDateParam)")
    public FreelancerPerformanceDTO getSummary(Long freelancerId, java.time.LocalDate startDateParam, java.time.LocalDate endDateParam) {
        MDC.put("userId", freelancerId.toString());
        try {
            contractReadClientService.getUser(freelancerId);

            LocalDateTime startDate = startDateParam.atStartOfDay();
            LocalDateTime endDate = endDateParam.atTime(23, 59, 59);

            FreelancerPerformanceProjection result = contractRepository.getFreelancerPerformance(freelancerId, startDate, endDate);

            if (result == null || result.getTotalContracts() == null || result.getTotalContracts() == 0) {
                return new FreelancerPerformanceDTO(freelancerId, 0, 0, 0, 0, 0);
            }

            long totalContracts = result.getTotalContracts();
            double averageContractValue = result.getAverageContractValue() != null ? result.getAverageContractValue() : 0.0;
            double totalEarnings = result.getTotalEarnings() != null ? result.getTotalEarnings() : 0.0;
            double completionRate = result.getCompletionRate() != null ? result.getCompletionRate() : 0.0;
            double averageDurationDays = result.getAverageDurationDays() != null ? result.getAverageDurationDays() : 0.0;

            return new FreelancerPerformanceDTO(freelancerId, totalContracts, averageContractValue,
                    completionRate, averageDurationDays, totalEarnings);
        } finally {
            MDC.remove("userId");
        }
    }
}
