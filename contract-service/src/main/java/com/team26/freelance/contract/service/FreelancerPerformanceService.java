package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.FreelancerPerformanceDTO;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.client.UserClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class FreelancerPerformanceService {

    private final ContractRepository contractRepository;
    private final UserClient userClient;

    public FreelancerPerformanceService(ContractRepository contractRepository, UserClient userClient) {
        this.contractRepository = contractRepository;
        this.userClient = userClient;
    }

    public FreelancerPerformanceDTO getSummary(Long freelancerId, java.time.LocalDate startDateParam, java.time.LocalDate endDateParam) {
        try {
            userClient.getUserById(freelancerId);
        } catch (feign.FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error verifying user");
        }

        LocalDateTime startDate = startDateParam.atStartOfDay();
        LocalDateTime endDate = endDateParam.atTime(23, 59, 59);

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
