package com.team26.freelance.contract.service;

import com.team26.freelance.common.event.ObservabilityAction;
import com.team26.freelance.contract.adapter.CassandraRowAdapter;
import com.team26.freelance.contract.dto.ContractMilestoneDTO;
import com.team26.freelance.contract.dto.BatchStatusUpdateRequestDTO;
import com.team26.freelance.contract.dto.ContractDateRangeDTO;
import com.team26.freelance.contract.dto.ContractSummaryDTO;
import com.team26.freelance.contract.dto.MilestoneTrackingRequest;
import com.team26.freelance.contract.dto.MilestoneTrackingResponse;
import com.team26.freelance.contract.model.MilestoneStatus;
import com.team26.freelance.contract.model.cassandra.ContractMilestoneEvent;
import com.team26.freelance.contract.model.cassandra.ContractMilestoneEventKey;
import com.team26.freelance.contract.observer.ContractEventSubject;
import com.team26.freelance.contract.messaging.publishers.ContractSagaPublisher;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.repository.cassandra.ContractMilestoneEventRepository;
import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.contracts.dto.UserDTO;
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.UserContractSummaryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContractService {
    private static final Logger log = LoggerFactory.getLogger(ContractService.class);
    private static final long SLOW_OPERATION_THRESHOLD_MS = 1000L;

    private final ContractRepository contractRepository;
    private final ContractCacheEvictionService cacheEvictionService;
    private final ContractMilestoneEventRepository contractMilestoneEventRepository;
    private final ContractEventSubject contractEventSubject;
    private final ContractAnalyticsService contractAnalyticsService;
    private final ContractSagaPublisher contractSagaPublisher;
    private final CassandraTemplate cassandraTemplate;
    private final CassandraRowAdapter cassandraRowAdapter;
    private final ContractReadClientService contractReadClientService;

    public ContractService(ContractRepository contractRepository,
            ContractCacheEvictionService cacheEvictionService,
            ContractMilestoneEventRepository contractMilestoneEventRepository,
            ContractEventSubject contractEventSubject,
            ContractAnalyticsService contractAnalyticsService,
            ContractSagaPublisher contractSagaPublisher,
            CassandraTemplate cassandraTemplate,
            CassandraRowAdapter cassandraRowAdapter,
            ContractReadClientService contractReadClientService) {
        this.contractRepository = contractRepository;
        this.cacheEvictionService = cacheEvictionService;
        this.contractMilestoneEventRepository = contractMilestoneEventRepository;
        this.contractEventSubject = contractEventSubject;
        this.contractAnalyticsService = contractAnalyticsService;
        this.contractSagaPublisher = contractSagaPublisher;
        this.cassandraTemplate = cassandraTemplate;
        this.cassandraRowAdapter = cassandraRowAdapter;
        this.contractReadClientService = contractReadClientService;
    }

    @Cacheable(value = "contract-s4-f6", key = "@contractCacheKeys.featureKey('S4-F6', #startDate, #endDate, #status)")
    public List<ContractDateRangeDTO> getContractHistory(LocalDate startDate, LocalDate endDate, ContractStatus status) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Contract> contracts;
        if (status != null) {
            contracts = contractRepository.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(startDateTime, endDateTime, status);
        } else {
            contracts = contractRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(startDateTime, endDateTime);
        }

        return contracts.stream()
                .map(c -> ContractDateRangeDTO.builder()
                        .id(c.getId())
                        .jobId(c.getJobId())
                        .freelancerId(c.getFreelancerId())
                        .clientId(c.getClientId())
                        .proposalId(c.getProposalId())
                        .agreedAmount(c.getAgreedAmount())
                        .status(c.getStatus())
                        .startDate(c.getStartDate())
                        .endDate(c.getEndDate())
                        .metadata(c.getMetadata())
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();
    }

    @Cacheable(value = "contract-s4-f5", key = "@contractCacheKeys.featureKey('S4-F5', #key, #operator, #value)")
    public List<Contract> searchByMetadata(String key, String operator, String value) {
        String normalizedOperator = operator == null ? "" : operator.trim().toLowerCase(Locale.ROOT);

        if ("gt".equals(normalizedOperator) || "lt".equals(normalizedOperator)) {
            try {
                Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Value must be numeric for operator: " + normalizedOperator);
            }
        }

        return switch (normalizedOperator) {
            case "eq" -> contractRepository.findByMetadataEquals(key, value);
            case "gt" -> contractRepository.findByMetadataGreaterThan(key, value);
            case "lt" -> contractRepository.findByMetadataLessThan(key, value);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "operator must be one of: eq, gt, lt");
        };
    }

    @Transactional
    public Contract update(Long id, Contract contractDetails) {
    MDC.put("contractId", id.toString());
    try {
        Contract contract = getContractById(id);
        ContractStatus oldStatus = contract.getStatus();

        if (contractDetails.getStatus() != null) {
            boolean validStatus = contract.getStatus().isValidTransitionTo(contractDetails.getStatus());
            if (!validStatus) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid status transition for contract " + contract.getId() + ": " + contract.getStatus()
                                + " -> " + contractDetails.getStatus());
            }
            contract.setStatus(contractDetails.getStatus());
        }

        if (contractDetails.getJobId() != null)
            contract.setJobId(contractDetails.getJobId());
        if (contractDetails.getFreelancerId() != null)
            contract.setFreelancerId(contractDetails.getFreelancerId());
        if (contractDetails.getClientId() != null)
            contract.setClientId(contractDetails.getClientId());
        if (contractDetails.getProposalId() != null)
            contract.setProposalId(contractDetails.getProposalId());
        if (contractDetails.getAgreedAmount() != null)
            contract.setAgreedAmount(contractDetails.getAgreedAmount());

        if (contractDetails.getStartDate() != null)
            contract.setStartDate(contractDetails.getStartDate());
        if (contractDetails.getEndDate() != null)
            contract.setEndDate(contractDetails.getEndDate());
        if (contractDetails.getMetadata() != null)
            contract.setMetadata(contractDetails.getMetadata());

        Contract saved = contractRepository.save(contract);
        log.info("Contract {} saved with status={}", saved.getId(), saved.getStatus());
        cacheEvictionService.evictAfterContractMutation(saved.getId());
        recordContractEvent(ObservabilityAction.CONTRACT_UPDATED, saved.getId(), buildUpdateDetails(contractDetails));

        if (contractDetails.getStatus() != null && oldStatus != contractDetails.getStatus()) {
            contractSagaPublisher.publishContractStatusChanged(saved.getId(), oldStatus, contractDetails.getStatus());
        }

        return saved;
    } finally {
        MDC.remove("contractId");
    }
    }

    public void delete(Long id) {
        MDC.put("contractId", id.toString());
        try {
            Contract contract = getContractById(id);
            contractRepository.delete(contract);
            cacheEvictionService.evictAfterContractMutation(id);
            recordContractEvent(ObservabilityAction.CONTRACT_DELETED, id, buildContractSnapshot(contract));
        } finally {
            MDC.remove("contractId");
        }
    }

    @Transactional
    public int updateStatusesRaw(List<BatchStatusUpdateRequestDTO> request) {
        Set<Long> uniqueIds = request.stream()
                .map(BatchStatusUpdateRequestDTO::getContractId)
                .collect(Collectors.toSet());

        List<Contract> contracts = contractRepository.findAllById(uniqueIds);
        Map<Long, Contract> contractById = contracts.stream()
                .collect(Collectors.toMap(Contract::getId, Function.identity()));

        List<Long> missingIds = uniqueIds.stream()
                .filter(id -> !contractById.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contracts not found: " + missingIds);
        }

        List<Contract> toSave = new ArrayList<>(request.size());
        List<Runnable> pendingEvents = new ArrayList<>();
        for (BatchStatusUpdateRequestDTO requestItem : request) {
            Long contractId = requestItem.getContractId();
            ContractStatus targetStatus = requestItem.getStatus();
            Contract contract = contractById.get(contractId);
            ContractStatus oldStatus = contract.getStatus();

            boolean valid = contract.getStatus().isValidTransitionTo(targetStatus);

            if (!valid) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid status transition for contract " + contract.getId() + ": " + contract.getStatus()
                                + " -> " + targetStatus);
            }

            contract.setStatus(targetStatus);
            // Set end_date if transitioning to COMPLETED or TERMINATED
            if (targetStatus == ContractStatus.COMPLETED || targetStatus == ContractStatus.TERMINATED) {
                if (contract.getEndDate() == null) {
                    contract.setEndDate(LocalDateTime.now());
                }
            }
            toSave.add(contract);

            if (oldStatus != targetStatus) {
                long finalContractId = contractId;
                pendingEvents.add(() -> contractSagaPublisher.publishContractStatusChanged(finalContractId, oldStatus, targetStatus));
            }
        }

        contractRepository.saveAll(toSave);
        for (Contract saved : toSave) {
            MDC.put("contractId", saved.getId().toString());
            try {
                log.info("Contract {} saved with status={}", saved.getId(), saved.getStatus());
            } finally {
                MDC.remove("contractId");
            }
        }
        cacheEvictionService.evictAfterContractsMutated(uniqueIds);

        // Publish events only after database commit completes successfully
        pendingEvents.forEach(Runnable::run);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("updatedCount", toSave.size());
        details.put("contractIds", new ArrayList<>(uniqueIds));
        List<String> statuses = request.stream()
            .map(BatchStatusUpdateRequestDTO::getStatus)
            .filter(Objects::nonNull)
            .map(Enum::name)
            .distinct()
            .toList();
        details.put("statuses", statuses);
        recordContractEvent(ObservabilityAction.BATCH_STATUS_UPDATED, null, details);
        return toSave.size();
    }

    public List<Contract> getAllContracts() {
        return contractRepository.findAll();
    }

    @Cacheable(value = "contract-detail", key = "@contractCacheKeys.contractDetail(#id)")
    public Contract getContractById(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"));
    }

    public ContractDTO getContractDtoById(Long id) {
        MDC.put("contractId", id.toString());
        try {
            return toContractDTO(getContractById(id));
        } finally {
            MDC.remove("contractId");
        }
    }

    @Cacheable(value = "contract-s4-f1", key = "@contractCacheKeys.featureKeyWithId('S4-F1', #userId)")
    public Contract getActiveContractForUser(Long userId) {
        MDC.put("userId", userId.toString());
        try {
            try {
                contractReadClientService.getUser(userId);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                }
            } catch (Exception e) {
                // ignore
            }
            return contractRepository
                    .findFirstByFreelancerIdAndStatusOrClientIdAndStatusOrderByCreatedAtDesc(
                            userId, ContractStatus.ACTIVE, userId, ContractStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Active contract not found"));
        } finally {
            MDC.remove("userId");
        }
    }

    public UserContractSummaryDTO getUserContractSummary(Long userId) {
        MDC.put("userId", userId.toString());
        try {
            Object[] summary = contractRepository.getUserContractSummary(userId);
            if (summary != null && summary.length == 1 && summary[0] instanceof Object[] nested) {
                summary = nested;
            }

            return new UserContractSummaryDTO(
                    numberAsLong(summary, 0),
                    numberAsLong(summary, 1),
                    numberAsLong(summary, 2),
                    numberAsBigDecimal(summary, 3),
                    numberAsBigDecimal(summary, 4));
        } finally {
            MDC.remove("userId");
        }
    }

    public int getActiveContractCountForUser(Long userId) {
        MDC.put("userId", userId.toString());
        try {
            return Math.toIntExact(contractRepository.countByFreelancerIdAndStatus(userId, ContractStatus.ACTIVE));
        } finally {
            MDC.remove("userId");
        }
    }

    public long getCompletedContractCountForUser(Long userId) {
        MDC.put("userId", userId.toString());
        try {
            return contractRepository.countByFreelancerIdAndStatus(userId, ContractStatus.COMPLETED);
        } finally {
            MDC.remove("userId");
        }
    }

    public int getActiveContractCountForJob(Long jobId) {
        MDC.put("jobId", jobId.toString());
        try {
            return Math.toIntExact(contractRepository.countByJobIdAndStatus(jobId, ContractStatus.ACTIVE));
        } finally {
            MDC.remove("jobId");
        }
    }

    public ContractDTO getActiveContractForProposal(Long proposalId) {
        MDC.put("proposalId", proposalId.toString());
        try {
            Contract contract = contractRepository.findFirstByProposalIdAndStatusOrderByCreatedAtDesc(
                            proposalId, ContractStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Active contract not found"));
            MDC.put("contractId", contract.getId().toString());
            return toContractDTO(contract);
        } finally {
            MDC.remove("contractId");
            MDC.remove("proposalId");
        }
    }

    public ContractDTO getContractForProposalByStatus(Long proposalId, ContractStatus status) {
        MDC.put("proposalId", proposalId.toString());
        try {
            Contract contract = contractRepository.findFirstByProposalIdAndStatusOrderByCreatedAtDesc(
                            proposalId, status)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Contract not found for proposal and status"));
            MDC.put("contractId", contract.getId().toString());
            return toContractDTO(contract);
        } finally {
            MDC.remove("contractId");
            MDC.remove("proposalId");
        }
    }

    @Transactional
public Contract createContract(Contract contract) {
    putMdc("jobId", contract.getJobId());
    putMdc("proposalId", contract.getProposalId());
    putMdc("userId", contract.getFreelancerId());
    try {
        if (contract.getStatus() == null) contract.setStatus(ContractStatus.ACTIVE);
        if (contract.getStartDate() == null) contract.setStartDate(LocalDateTime.now());
        if (contract.getCreatedAt() == null) contract.setCreatedAt(LocalDateTime.now());

        Contract saved = contractRepository.save(contract);
        putMdc("contractId", saved.getId());
        log.info("Contract {} saved with status={}", saved.getId(), saved.getStatus());
        cacheEvictionService.evictAfterContractCreated();
        recordContractEvent(ObservabilityAction.CONTRACT_CREATED, saved.getId(), buildContractSnapshot(saved));
        contractSagaPublisher.publishContractCreated(
                saved.getId(),
                saved.getProposalId(),
                saved.getJobId(),
                saved.getFreelancerId(),
                saved.getAgreedAmount());
        return saved;
    } finally {
        MDC.remove("contractId");
        MDC.remove("jobId");
        MDC.remove("proposalId");
        MDC.remove("userId");
    }
}
    @Cacheable(value = "contract-s4-f3", key = "@contractCacheKeys.featureKey('S4-F3', #minAmount, #maxAmount, #status)")
    public List<ContractSummaryDTO> findContractsByBudgetRangeWithFreelancerInfo(Double minAmount,
            Double maxAmount,
            String status) {
        long startedAt = System.nanoTime();
        ContractStatus parsedStatus = parseOptionalStatus(status);
        List<Contract> contracts = parsedStatus == null
                ? contractRepository.findByAgreedAmountBetweenOrderByAgreedAmountDesc(minAmount, maxAmount)
                : contractRepository.findByAgreedAmountBetweenAndStatusOrderByAgreedAmountDesc(
                        minAmount, maxAmount, parsedStatus);
        List<ContractSummaryDTO> contractSummaries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, String> freelancerNames = new ConcurrentHashMap<>();
        Map<Long, String> jobTitles = new ConcurrentHashMap<>();

        Set<Long> freelancerIds = contracts.stream().map(Contract::getFreelancerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> jobIds = contracts.stream().map(Contract::getJobId).filter(Objects::nonNull).collect(Collectors.toSet());

freelancerIds.parallelStream().forEach(id -> {
    try {
        UserDTO user = contractReadClientService.getUser(id);
        freelancerNames.put(id, user != null && user.getName() != null ? user.getName() : "Unknown User");
    } catch (Exception e) {
        freelancerNames.put(id, "Unknown User");
    }
});
jobIds.parallelStream().forEach(id -> {
    try {
        JobDTO job = contractReadClientService.getJob(id);
        jobTitles.put(id, job != null && job.getTitle() != null ? job.getTitle() : "Unknown Job");
    } catch (Exception e) {
        jobTitles.put(id, "Unknown Job");
    }
});

try {
            for (Contract contract : contracts) {
                putMdc("contractId", contract.getId());
                putMdc("userId", contract.getFreelancerId());
                putMdc("jobId", contract.getJobId());
                String freelancerName = freelancerNames.getOrDefault(contract.getFreelancerId(), "Unknown User");
                String jobTitle = jobTitles.getOrDefault(contract.getJobId(), "Unknown Job");
                LocalDateTime endDate = contract.getEndDate() == null ? now : contract.getEndDate();
                long durationDays = contract.getStartDate() == null
                        ? 0 : ChronoUnit.DAYS.between(contract.getStartDate(), endDate);
                contractSummaries.add(new ContractSummaryDTO(
                        contract.getId(), freelancerName, jobTitle,
                        contract.getAgreedAmount(),
                        contract.getStatus() == null ? null : contract.getStatus().name(),
                        durationDays));
            }
        } finally {
            MDC.remove("contractId");
            MDC.remove("userId");
            MDC.remove("jobId");
            logSlowOperation("contract-search-enrichment", startedAt);
        }
        return contractSummaries;
    }

    @Transactional
    public Contract updateContractProgress(Long contractId, Map<String, Object> incomingMetadata) {
        MDC.put("contractId", contractId.toString());
        try {
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Contract not found"));

            Map<String, Object> existingMetadata = contract.getMetadata();
            Map<String, Object> newMetadata = new HashMap<>();
            if (existingMetadata != null) {
                newMetadata.putAll(existingMetadata);
            }
            if (incomingMetadata != null) {
                newMetadata.putAll(incomingMetadata);
            }
            contract.setMetadata(newMetadata);

            Contract saved = contractRepository.save(contract);
            log.info("Contract {} saved with status={}", saved.getId(), saved.getStatus());
            cacheEvictionService.evictAfterContractMutation(saved.getId());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("metadata", incomingMetadata == null ? Map.of() : incomingMetadata);
            recordContractEvent(ObservabilityAction.PROGRESS_UPDATED, saved.getId(), details);
            return saved;
        } finally {
            MDC.remove("contractId");
        }
    }

    public MilestoneTrackingResponse trackMilestone(Long contractId, MilestoneTrackingRequest request) {
        MDC.put("contractId", contractId.toString());
        try {
            Contract contract = getContractById(contractId);

            MilestoneStatus milestoneStatus;
            try {
                milestoneStatus = MilestoneStatus.valueOf(request.getStatus().trim().toUpperCase());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }

            LocalDateTime now = LocalDateTime.now();
            ContractMilestoneEvent event = new ContractMilestoneEvent(
                    new ContractMilestoneEventKey(contract.getId(), now),
                    request.getMilestoneOrder(),
                    milestoneStatus.name(),
                    request.getRecordedBy(),
                    request.getNotes());

            ContractMilestoneEvent savedEvent = contractMilestoneEventRepository.save(event);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("contractId", contract.getId());
            details.put("milestoneOrder", request.getMilestoneOrder());
            details.put("status", milestoneStatus.name());
            details.put("recordedBy", request.getRecordedBy());
            details.put("notes", request.getNotes());
            details.put("summary", buildMilestoneSummary(contract.getId(), request.getMilestoneOrder(), milestoneStatus,
                    request.getRecordedBy(), request.getNotes()));

            recordContractEvent(ObservabilityAction.MILESTONE_TRACKED, contract.getId(), details);
            cacheEvictionService.evictMilestoneTimeline(contractId);

            return new MilestoneTrackingResponse(
                    savedEvent.getContractId(),
                    savedEvent.getTimestamp(),
                    savedEvent.getMilestoneOrder(),
                    savedEvent.getStatus(),
                    savedEvent.getRecordedBy(),
                    savedEvent.getNotes());
        } finally {
            MDC.remove("contractId");
        }
    }

    private ContractStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ContractStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }
    }

    private ContractDTO toContractDTO(Contract contract) {
        return new ContractDTO(
                contract.getId(),
                contract.getJobId(),
                contract.getFreelancerId(),
                contract.getClientId(),
                contract.getProposalId(),
                contract.getAgreedAmount(),
                contract.getStatus() == null ? null : contract.getStatus().name(),
                contract.getStartDate(),
                contract.getEndDate());
    }

    private Long numberAsLong(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return 0L;
        }
        return ((Number) row[index]).longValue();
    }

    private BigDecimal numberAsBigDecimal(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return BigDecimal.ZERO;
        }
        Object value = row[index];
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
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

    private String buildMilestoneSummary(Long contractId, Integer milestoneOrder, MilestoneStatus milestoneStatus,
            String recordedBy, String notes) {
        StringBuilder summary = new StringBuilder();
        summary.append("Contract ").append(contractId)
                .append(" milestone ").append(milestoneOrder)
                .append(" marked ").append(milestoneStatus.name())
                .append(" by ").append(recordedBy);
        if (notes != null && !notes.isBlank()) {
            summary.append(": ").append(notes);
        }
        return summary.toString();
    }

    private void recordContractEvent(ObservabilityAction action, Long contractId, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("details", details == null ? Map.of() : details);
        contractEventSubject.notifyObservers(action.name(), payload);
    }

    private Map<String, Object> buildContractSnapshot(Contract contract) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (contract == null) {
            return details;
        }
        details.put("jobId", contract.getJobId());
        details.put("freelancerId", contract.getFreelancerId());
        details.put("clientId", contract.getClientId());
        details.put("proposalId", contract.getProposalId());
        details.put("agreedAmount", contract.getAgreedAmount());
        details.put("status", contract.getStatus() == null ? null : contract.getStatus().name());
        details.put("startDate", contract.getStartDate());
        details.put("endDate", contract.getEndDate());
        details.put("metadata", contract.getMetadata());
        details.put("createdAt", contract.getCreatedAt());
        return details;
    }

    private Map<String, Object> buildUpdateDetails(Contract contractDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (contractDetails == null) {
            return details;
        }
        if (contractDetails.getJobId() != null) {
            details.put("jobId", contractDetails.getJobId());
        }
        if (contractDetails.getFreelancerId() != null) {
            details.put("freelancerId", contractDetails.getFreelancerId());
        }
        if (contractDetails.getClientId() != null) {
            details.put("clientId", contractDetails.getClientId());
        }
        if (contractDetails.getProposalId() != null) {
            details.put("proposalId", contractDetails.getProposalId());
        }
        if (contractDetails.getAgreedAmount() != null) {
            details.put("agreedAmount", contractDetails.getAgreedAmount());
        }
        if (contractDetails.getStatus() != null) {
            details.put("status", contractDetails.getStatus().name());
        }
        if (contractDetails.getStartDate() != null) {
            details.put("startDate", contractDetails.getStartDate());
        }
        if (contractDetails.getEndDate() != null) {
            details.put("endDate", contractDetails.getEndDate());
        }
        if (contractDetails.getMetadata() != null) {
            details.put("metadata", contractDetails.getMetadata());
        }
        return details;
    }
  
    @Cacheable(value = "contract-s4-f12", key = "@contractCacheKeys.featureKeyWithId('S4-F12', #contractId, #startTime, #endTime)")
    public List<ContractMilestoneDTO> getContractMilestoneTimeline(Long contractId, Instant startTime, Instant endTime) {
        getContractById(contractId);

        String cql;
        Object[] args;

        if (startTime != null && endTime != null) {
            cql = "SELECT * FROM contract_milestone_events WHERE contract_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC";
            args = new Object[]{contractId, startTime, endTime};
        } else {
            cql = "SELECT * FROM contract_milestone_events WHERE contract_id = ? ORDER BY timestamp DESC";
            args = new Object[]{contractId};
        }

        return cassandraTemplate.getCqlOperations().query(cql, (row, rowNum) -> cassandraRowAdapter.adapt(row), args);
    }

}
