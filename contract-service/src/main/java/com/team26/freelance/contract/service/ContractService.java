package com.team26.freelance.contract.service;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.service.dto.ContractStatusUpdateRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Transactional
    public Contract createContract(Contract contract) {
        // default status to ACTIVE if not provided
        if (contract.getStatus() == null) {
            contract.setStatus(ContractStatus.ACTIVE);
        }
        return contractRepository.save(contract);
    }

}
