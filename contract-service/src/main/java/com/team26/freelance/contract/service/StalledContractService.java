package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.StalledContractDTO;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.client.JobServiceClient;
import com.team26.freelance.contract.client.UserServiceClient;
import feign.FeignException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StalledContractService {

    private final ContractRepository contractRepository;
    private final UserServiceClient userServiceClient;
    private final JobServiceClient jobServiceClient;

    public StalledContractService(ContractRepository contractRepository,
                                  UserServiceClient userServiceClient,
                                  JobServiceClient jobServiceClient) {
        this.contractRepository = contractRepository;
        this.userServiceClient = userServiceClient;
        this.jobServiceClient = jobServiceClient;
    }

    @Cacheable(value = "contract-s4-f9", key = "@contractCacheKeys.featureKey('S4-F9', #maxProgress, #stalledDays)")
    public List<StalledContractDTO> getStalledContracts(double maxProgress, double stalledDays) {
        List<Object[]> rows = contractRepository.findStalledContractsNoJoin(maxProgress, stalledDays);
        List<StalledContractDTO> dtos = new ArrayList<>();

        Set<Long> freelancerIds = rows.stream().map(r -> ((Number) r[1]).longValue()).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> jobIds = rows.stream().map(r -> ((Number) r[2]).longValue()).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> freelancerMap = new java.util.concurrent.ConcurrentHashMap<>();
        Map<Long, String> jobMap = new java.util.concurrent.ConcurrentHashMap<>();

        freelancerIds.parallelStream().forEach(id -> {
            try {
                Map<String, Object> user = userServiceClient.getUser(id);
                freelancerMap.put(id, user != null && user.get("name") != null ? String.valueOf(user.get("name")) : "Unknown User");
            } catch (FeignException e) {
                freelancerMap.put(id, "Unknown User");
            }
        });

        jobIds.parallelStream().forEach(id -> {
            try {
                Map<String, Object> job = jobServiceClient.getJob(id);
                jobMap.put(id, job != null && job.get("title") != null ? String.valueOf(job.get("title")) : "Unknown Job");
            } catch (FeignException e) {
                jobMap.put(id, "Unknown Job");
            }
        });

        for (Object[] row : rows) {
            Long contractId = ((Number) row[0]).longValue();
            Long freelancerId = ((Number) row[1]).longValue();
            Long jobId = ((Number) row[2]).longValue();

            Double agreedAmount = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            Double progressPercentage = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            Double daysSinceLastActivity = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;

            String freelancerName = freelancerMap.getOrDefault(freelancerId, "Unknown User");
            String jobTitle = jobMap.getOrDefault(jobId, "Unknown Job");

            dtos.add(new StalledContractDTO(contractId, freelancerName, jobTitle, agreedAmount, progressPercentage, daysSinceLastActivity));
        }

        return dtos;
    }
}
