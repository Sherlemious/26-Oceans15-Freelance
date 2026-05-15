package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.StalledContractDTO;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.repository.ContractRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Service
public class StalledContractService {

    private static final Logger log = LoggerFactory.getLogger(StalledContractService.class);
    private static final long SLOW_OPERATION_THRESHOLD_MS = 1000L;

    private final ContractRepository contractRepository;
    private final ContractReadClientService contractReadClientService;

    public StalledContractService(ContractRepository contractRepository,
                                  ContractReadClientService contractReadClientService) {
        this.contractRepository = contractRepository;
        this.contractReadClientService = contractReadClientService;
    }

    @Cacheable(value = "contract-s4-f9", key = "@contractCacheKeys.featureKey('S4-F9', #maxProgress, #stalledDays)")
    public List<StalledContractDTO> getStalledContracts(double maxProgress, double stalledDays) {
        long startedAt = System.nanoTime();
        List<Contract> contracts = contractRepository.findStalledContractCandidates(maxProgress, stalledDays);
        List<StalledContractDTO> dtos = new ArrayList<>();
        Map<Long, String> freelancerNames = new HashMap<>();
        Map<Long, String> jobTitles = new HashMap<>();

        try {
            for (Contract contract : contracts) {
                putMdc("contractId", contract.getId());
                putMdc("userId", contract.getFreelancerId());
                putMdc("jobId", contract.getJobId());
                String freelancerName = freelancerNames.computeIfAbsent(
                        contract.getFreelancerId(),
                        freelancerId -> contractReadClientService.getUser(freelancerId).getName());
                String jobTitle = jobTitles.computeIfAbsent(
                        contract.getJobId(),
                        jobId -> contractReadClientService.getJob(jobId).getTitle());

                dtos.add(new StalledContractDTO(
                        contract.getId(),
                        freelancerName,
                        jobTitle,
                        contract.getAgreedAmount(),
                        progressPercentage(contract),
                        daysSinceLastActivity(contract)));
            }
        } finally {
            MDC.remove("contractId");
            MDC.remove("userId");
            MDC.remove("jobId");
            logSlowOperation("stalled-contract-enrichment", startedAt);
        }

        return dtos;
    }

    private Double progressPercentage(Contract contract) {
        Object value = metadataValue(contract, "progressPercentage");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            return Double.parseDouble(value.toString());
        }
        return 0.0;
    }

    private Double daysSinceLastActivity(Contract contract) {
        LocalDateTime lastActivity = parseLastActivity(contract);
        long elapsedHours = ChronoUnit.HOURS.between(lastActivity, LocalDateTime.now());
        return elapsedHours / 24.0;
    }

    private LocalDateTime parseLastActivity(Contract contract) {
        Object value = metadataValue(contract, "lastActivityDate");
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        if (value != null) {
            String text = value.toString();
            try {
                return LocalDateTime.parse(text);
            } catch (RuntimeException ignored) {
                try {
                    return LocalDate.parse(text).atStartOfDay();
                } catch (RuntimeException ignoredAgain) {
                    return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDateTime();
                }
            }
        }
        return contract.getCreatedAt();
    }

    private Object metadataValue(Contract contract, String key) {
        if (contract.getMetadata() == null) {
            return null;
        }
        return contract.getMetadata().get(key);
    }

    private void putMdc(String key, Long value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }

    private void logSlowOperation(String operationName, long startedAtNanos) {
        long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        if (elapsedMs > SLOW_OPERATION_THRESHOLD_MS) {
            log.warn("Slow {} took {}ms", operationName, elapsedMs);
        }
    }
}
