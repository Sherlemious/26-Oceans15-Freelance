package com.team26.freelance.contract.service;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContractService {

    private final ContractRepository contractRepository;

    public ContractService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
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
