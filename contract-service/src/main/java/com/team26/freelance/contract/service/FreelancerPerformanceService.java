package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.FreelancerPerformanceDTO;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class FreelancerPerformanceService {

    private final ContractRepository contractRepository;

    public FreelancerPerformanceService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    public FreelancerPerformanceDTO getSummary(Long freelancerId, String startDateStr, String endDateStr) {
        if (contractRepository.countUserById(freelancerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found");
        }

        LocalDateTime startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
        LocalDateTime endDate = LocalDateTime.parse(endDateStr + "T23:59:59");

        Object[][] result = contractRepository.getFreelancerPerformance(freelancerId, startDate, endDate);

        if (result == null || result.length == 0 || result[0] == null || result[0][0] == null) {
            return new FreelancerPerformanceDTO(freelancerId, 0, 0, 0, 0, 0);
        }

        Object[] row = result[0];
        long totalContracts = ((Number) row[0]).longValue();
        double averageContractValue = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
        double totalEarnings = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
        double completionRate = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
        double averageDurationDays = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;

        return new FreelancerPerformanceDTO(freelancerId, totalContracts, averageContractValue,
                completionRate, averageDurationDays, totalEarnings);
    }
}

