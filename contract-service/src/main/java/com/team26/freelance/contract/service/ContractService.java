package com.team26.freelance.contract.service;

import com.team26.freelance.contract.adapter.CassandraRowAdapter;
import com.team26.freelance.contract.dto.ContractMilestoneDTO;
import com.team26.freelance.contract.dto.ContractDateRangeDTO;
import com.team26.freelance.contract.dto.ContractSummaryDTO;
import com.team26.freelance.contract.dto.MilestoneTrackingRequest;
import com.team26.freelance.contract.dto.MilestoneTrackingResponse;
import com.team26.freelance.contract.model.MilestoneStatus;
import com.team26.freelance.contract.model.cassandra.ContractMilestoneEvent;
import com.team26.freelance.contract.model.cassandra.ContractMilestoneEventKey;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.repository.cassandra.ContractMilestoneEventRepository;
import com.team26.freelance.contract.service.ContractAnalyticsService;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractCacheEvictionService cacheEvictionService;
    private final ContractMilestoneEventRepository contractMilestoneEventRepository;
    private final ContractAnalyticsService contractAnalyticsService;
    private final CassandraTemplate cassandraTemplate;
    private final CassandraRowAdapter cassandraRowAdapter;

    public ContractService(ContractRepository contractRepository,
            ContractCacheEvictionService cacheEvictionService,
            ContractMilestoneEventRepository contractMilestoneEventRepository,
            ContractAnalyticsService contractAnalyticsService,
            CassandraTemplate cassandraTemplate,
            CassandraRowAdapter cassandraRowAdapter) {
        this.contractRepository = contractRepository;
        this.cacheEvictionService = cacheEvictionService;
        this.contractMilestoneEventRepository = contractMilestoneEventRepository;
        this.contractAnalyticsService = contractAnalyticsService;
        this.cassandraTemplate = cassandraTemplate;
        this.cassandraRowAdapter = cassandraRowAdapter;
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
        Contract contract = getContractById(id);

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
        cacheEvictionService.evictAfterContractMutation(saved.getId());
        return saved;
    }

    public void delete(Long id) {
        Contract contract = getContractById(id);
        contractRepository.delete(contract);
        cacheEvictionService.evictAfterContractMutation(id);
    }

    @Transactional
    public int updateStatusesRaw(Map<String, Object> request) {
        Object idsObj = request.get("ids");
        if (idsObj == null)
            idsObj = request.get("contractIds");
        if (idsObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing ids in request");
        }

        List<?> list = (List<?>) idsObj;
        Set<Long> uniqueIds = new HashSet<>();
        for (Object o : list) {
            uniqueIds.add(Long.valueOf(o.toString()));
        }

        List<Contract> contracts = contractRepository.findAllById(uniqueIds);
        Map<Long, Contract> contractById = contracts.stream()
                .collect(Collectors.toMap(Contract::getId, Function.identity()));

        List<Long> missingIds = uniqueIds.stream()
                .filter(id -> !contractById.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contracts not found: " + missingIds);
        }

        Object statusObj = request.get("status");
        if (statusObj == null)
            statusObj = request.get("newStatus");
        if (statusObj == null)
            statusObj = request.get("targetStatus");
        if (statusObj == null)
            statusObj = request.get("toStatus");
        if (statusObj == null)
            statusObj = request.get("contractStatus");

        String s = statusObj != null ? statusObj.toString().toUpperCase() : null;

        List<Contract> toSave = new ArrayList<>(uniqueIds.size());
        for (Long id : uniqueIds) {
            Contract contract = contractById.get(id);

            ContractStatus targetStatus;
            try {
                if (s == null)
                    throw new NullPointerException();
                targetStatus = ContractStatus.valueOf(s);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }

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
        }

        contractRepository.saveAll(toSave);
        cacheEvictionService.evictAfterContractsMutated(uniqueIds);
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

    @Cacheable(value = "contract-s4-f1", key = "@contractCacheKeys.featureKeyWithId('S4-F1', #userId)")
    public Contract getActiveContractForUser(Long userId) {
        return contractRepository
                .findFirstByFreelancerIdAndStatusOrClientIdAndStatusOrderByCreatedAtDesc(userId, ContractStatus.ACTIVE,
                        userId, ContractStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Active contract not found"));
    }

    @Transactional
    public Contract createContract(Contract contract) {
        // default status to ACTIVE if not provided
        if (contract.getStatus() == null) {
            contract.setStatus(ContractStatus.ACTIVE);
        }
        if (contract.getStartDate() == null) {
            contract.setStartDate(LocalDateTime.now());
        }
        if (contract.getCreatedAt() == null) {
            contract.setCreatedAt(LocalDateTime.now());
        }
        Contract saved = contractRepository.save(contract);
        cacheEvictionService.evictAfterContractCreated();
        return saved;
    }

    @Cacheable(value = "contract-s4-f3", key = "@contractCacheKeys.featureKey('S4-F3', #minAmount, #maxAmount, #status)")
    public List<ContractSummaryDTO> findContractsByBudgetRangeWithFreelancerInfo(Double minAmount,
            Double maxAmount,
            String status) {
        List<Object[]> rows = contractRepository.findContractsByBudgetRangeWithFreelancerInfo(minAmount, maxAmount);
        List<ContractSummaryDTO> contractSummaries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Object[] row : rows) {
            String rowStatus = row[4] != null ? row[4].toString() : null;
            if (status != null && !status.isEmpty() && !status.equalsIgnoreCase(rowStatus)) {
                continue;
            }

            LocalDateTime startDate = null;
            if (row[5] instanceof Timestamp) {
                startDate = ((Timestamp) row[5]).toLocalDateTime();
            } else if (row[5] instanceof LocalDateTime) {
                startDate = (LocalDateTime) row[5];
            } else if (row[5] != null) {
                startDate = LocalDateTime.parse(row[5].toString());
            }

            LocalDateTime endDate = now;
            if (row[6] != null) {
                if (row[6] instanceof Timestamp) {
                    endDate = ((Timestamp) row[6]).toLocalDateTime();
                } else if (row[6] instanceof LocalDateTime) {
                    endDate = (LocalDateTime) row[6];
                } else {
                    endDate = LocalDateTime.parse(row[6].toString());
                }
            }

            long durationDays = 0;
            if (startDate != null) {
                durationDays = ChronoUnit.DAYS.between(startDate, endDate);
            }

            ContractSummaryDTO contractSummary = new ContractSummaryDTO(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    ((Number) row[3]).doubleValue(),
                    row[4] != null ? row[4].toString() : null,
                    durationDays);
            contractSummaries.add(contractSummary);
        }

        return contractSummaries;
    }

    @Transactional
    public Contract updateContractProgress(Long contractId, Map<String, Object> incomingMetadata) {
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
        cacheEvictionService.evictAfterContractMutation(saved.getId());
        return saved;
    }

    public MilestoneTrackingResponse trackMilestone(Long contractId, MilestoneTrackingRequest request) {
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

        contractAnalyticsService.notifyObservers("MILESTONE_TRACKED", details);

        return new MilestoneTrackingResponse(
                savedEvent.getContractId(),
                savedEvent.getTimestamp(),
                savedEvent.getMilestoneOrder(),
                savedEvent.getStatus(),
                savedEvent.getRecordedBy(),
                savedEvent.getNotes());
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

    @Cacheable(value = "contract-s4-f12", key = "'contract-service::S4-F12::' + #contractId")
    public List<ContractMilestoneDTO> getContractMilestoneTimeline(Long contractId, Instant startTime, Instant endTime) {
        getContractById(contractId);

        String cql;
        Object[] args;

        if (startTime != null && endTime != null) {
            cql = "SELECT * FROM contract_milestone_events WHERE contract_id = ? AND timestamp >= ? AND timestamp <= ?";
            args = new Object[]{contractId, startTime, endTime};
        } else {
            cql = "SELECT * FROM contract_milestone_events WHERE contract_id = ?";
            args = new Object[]{contractId};
        }

        return cassandraTemplate.getCqlOperations().query(cql, (row, rowNum) -> cassandraRowAdapter.adapt(row), args);
    }

}
