package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.ContractSummaryDTO;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

    public List<ContractSummaryDTO> findContractsByBudgetRangeWithFreelancerInfo(Double minAmount,
                                                                                 Double maxAmount,
                                                                                 String status) {
        List<Object[]> rows = contractRepository.findContractsByBudgetRangeWithFreelancerInfo(minAmount, maxAmount, status);
        List<ContractSummaryDTO> contractSummaries = new ArrayList<>();

        for (Object[] row : rows) {
            Timestamp startTimestamp = (Timestamp) row[5];
            Timestamp endTimestamp = row[6] != null ? (Timestamp) row[6] : null;

            LocalDateTime startDate = startTimestamp.toLocalDateTime();
            LocalDateTime endDate = endTimestamp != null ? endTimestamp.toLocalDateTime() : LocalDateTime.now();
            long durationDays = ChronoUnit.DAYS.between(startDate, endDate);

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

}
