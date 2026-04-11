package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.ContractSummaryDTO;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.client.UserClient;
import com.team26.freelance.contract.service.dto.ContractStatusUpdateRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final UserClient userClient;

    public ContractService(ContractRepository contractRepository, UserClient userClient) {
        this.contractRepository = contractRepository;
        this.userClient = userClient;
    }

    public List<Contract> getContractHistory(LocalDate startDate, LocalDate endDate, ContractStatus status) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        if (status != null) {
            return contractRepository.findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(startDateTime, endDateTime, status);
        }

        return contractRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(startDateTime, endDateTime);
    }

    public List<Contract> searchByMetadata(String key, String operator, String value) {
        String normalizedOperator = operator == null ? "" : operator.trim().toLowerCase(Locale.ROOT);

        if ("gt".equals(normalizedOperator) || "lt".equals(normalizedOperator)) {
            try {
                Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Value must be numeric for operator: " + normalizedOperator
                );
            }
        }

        return switch (normalizedOperator) {
            case "eq" -> contractRepository.findByMetadataEquals(key, value);
            case "gt" -> contractRepository.findByMetadataGreaterThan(key, value);
            case "lt" -> contractRepository.findByMetadataLessThan(key, value);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "operator must be one of: eq, gt, lt"
            );
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
                        "Invalid status transition for contract " + contract.getId() + ": " + contract.getStatus() + " -> " + contractDetails.getStatus()
                );
            }
            contract.setStatus(contractDetails.getStatus());
        }
        
        if (contractDetails.getJobId() != null) contract.setJobId(contractDetails.getJobId());
        if (contractDetails.getFreelancerId() != null) contract.setFreelancerId(contractDetails.getFreelancerId());
        if (contractDetails.getClientId() != null) contract.setClientId(contractDetails.getClientId());
        if (contractDetails.getProposalId() != null) contract.setProposalId(contractDetails.getProposalId());
        if (contractDetails.getAgreedAmount() != null) contract.setAgreedAmount(contractDetails.getAgreedAmount());
        
        if (contractDetails.getStartDate() != null) contract.setStartDate(contractDetails.getStartDate());
        if (contractDetails.getEndDate() != null) contract.setEndDate(contractDetails.getEndDate());
        if (contractDetails.getMetadata() != null) contract.setMetadata(contractDetails.getMetadata());
        
        return contractRepository.save(contract);
    }

    public void delete(Long id) {
        Contract contract = getContractById(id);
        contractRepository.delete(contract);
    }

    @Transactional
    public int updateStatusesRaw(Map<String, Object> request) {
        Object idsObj = request.get("ids");
        if (idsObj == null) idsObj = request.get("contractIds");
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
        if (statusObj == null) statusObj = request.get("newStatus");
        if (statusObj == null) statusObj = request.get("targetStatus");
        if (statusObj == null) statusObj = request.get("toStatus");
        if (statusObj == null) statusObj = request.get("contractStatus");

        String s = statusObj != null ? statusObj.toString().toUpperCase() : null;

        List<Contract> toSave = new ArrayList<>(uniqueIds.size());
        for (Long id : uniqueIds) {
            Contract contract = contractById.get(id);

            ContractStatus targetStatus;
            try {
                if (s == null) throw new NullPointerException();
                targetStatus = ContractStatus.valueOf(s);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }

            boolean valid = contract.getStatus().isValidTransitionTo(targetStatus);

            if (!valid) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid status transition for contract " + contract.getId() + ": " + contract.getStatus() + " -> " + targetStatus
                );
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
        return toSave.size();
    }

    public List<Contract> getAllContracts() {
        return contractRepository.findAll();
    }

    public Contract getContractById(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"
                ));
    }

    public Contract getActiveContractForUser(Long userId) {
        try {
            userClient.getUserById(userId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        } catch (Exception e) {
            // If the service is down or other error, we can still try to get the contract, or fail.
            // But let's assume it passes if user exists.
            // We can just log it or ignore for now to allow returning the contract if it exists.
        }

        return contractRepository.findFirstByFreelancerIdAndStatusOrClientIdAndStatusOrderByCreatedAtDesc(userId, ContractStatus.ACTIVE, userId, ContractStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Active contract not found"
                ));
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
        return contractRepository.save(contract);
    }

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
                    durationDays
            );
            contractSummaries.add(contractSummary);
        }

        return contractSummaries;
    }
    @Transactional
    public Contract updateContractProgress(Long contractId, Map<String, Object> incomingMetadata) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"
                ));

        Map<String, Object> existingMetadata = contract.getMetadata();
        Map<String, Object> newMetadata = new HashMap<>();
        if (existingMetadata != null) {
            newMetadata.putAll(existingMetadata);
        }
        if (incomingMetadata != null) {
            newMetadata.putAll(incomingMetadata);
        }
        contract.setMetadata(newMetadata);

        return contractRepository.save(contract);
    }

}
