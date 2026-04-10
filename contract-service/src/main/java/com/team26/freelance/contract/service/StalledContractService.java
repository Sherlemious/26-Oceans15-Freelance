package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.StalledContractDTO;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StalledContractService {

    private final ContractRepository contractRepository;

    public StalledContractService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    public List<StalledContractDTO> getStalledContracts(double maxProgress, double stalledDays) {
        List<Object[]> rows = contractRepository.findStalledContracts(maxProgress, stalledDays);
        List<StalledContractDTO> dtos = new ArrayList<>();

        for (Object[] row : rows) {
            Long contractId = ((Number) row[0]).longValue();
            String freelancerName = (String) row[1];
            String jobTitle = (String) row[2];
            Double agreedAmount = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            Double progressPercentage = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            Double daysSinceLastActivity = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;

            dtos.add(new StalledContractDTO(contractId, freelancerName, jobTitle, agreedAmount, progressPercentage, daysSinceLastActivity));
        }

        return dtos;
    }
}

