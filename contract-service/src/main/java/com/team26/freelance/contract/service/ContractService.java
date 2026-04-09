package com.team26.freelance.contract.service;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ContractService {

    private final ContractRepository contractRepository;

    public ContractService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
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