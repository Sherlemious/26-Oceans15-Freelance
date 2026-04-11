package com.team26.freelance.contract.service;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.service.dto.ContractStatusUpdateRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public ContractService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
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

        boolean validStatus = contract.getStatus().isValidTransitionTo(contractDetails.getStatus());
        if (!validStatus) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid status transition for contract " + contract.getId() + ": " + contract.getStatus() + " -> " + contractDetails.getStatus()
            );
        }
        
        contract.setJobId(contractDetails.getJobId());
        contract.setFreelancerId(contractDetails.getFreelancerId());
        contract.setClientId(contractDetails.getClientId());
        contract.setProposalId(contractDetails.getProposalId());
        contract.setAgreedAmount(contractDetails.getAgreedAmount());
        contract.setStatus(contractDetails.getStatus());
        contract.setStartDate(contractDetails.getStartDate());
        contract.setEndDate(contractDetails.getEndDate());
        contract.setMetadata(contractDetails.getMetadata());
        
        return contractRepository.save(contract);
    }

    public void delete(Long id) {
        Contract contract = getContractById(id);
        contractRepository.delete(contract);
    }

    @Transactional
    public int updateStatuses(List<ContractStatusUpdateRequest> updates) {
        Set<Long> uniqueIds = new HashSet<>();
        for (ContractStatusUpdateRequest update : updates) {
            if (!uniqueIds.add(update.contractId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate contractId in request: " + update.contractId());
            }
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

        List<Contract> toSave = new ArrayList<>(updates.size());
        for (ContractStatusUpdateRequest update : updates) {
            Contract contract = contractById.get(update.contractId());

            boolean valid = contract.getStatus().isValidTransitionTo(update.status());
            
            if (!valid) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid status transition for contract " + contract.getId() + ": " + contract.getStatus() + " -> " + update.status()
                );
            }

            contract.setStatus(update.status());
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
        if (contractRepository.countUsersById(userId) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User not found"
            );
        }

        return contractRepository.findMostRecentActiveContractByUserId(userId)
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
        return contractRepository.save(contract);
    }

    @Transactional
    public Contract updateContractProgress(Long contractId, Map<String, Object> incomingMetadata) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"
                ));

        Map<String, Object> existingMetadata = contract.getMetadata();
        if (existingMetadata == null) {
            existingMetadata = new HashMap<>();
        }

        existingMetadata.putAll(incomingMetadata);
        contract.setMetadata(existingMetadata);

        return contractRepository.save(contract);
    }

}
