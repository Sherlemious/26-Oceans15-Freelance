package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.FreelancerPerformanceDTO;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.client.UserClient;
import com.team26.freelance.contract.repository.FreelancerPerformanceProjection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.time.LocalDate;

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
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found");
        } catch (Exception e) {
            long count = contractRepository.countByFreelancerId(freelancerId);
            if (count == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found");
            }
        }

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
    }
}
