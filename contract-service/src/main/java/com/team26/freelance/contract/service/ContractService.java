package com.team26.freelance.contract.service;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.repository.ContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ContractService {

    private final ContractRepository contractRepository;

    public ContractService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    public Contract create(Contract contract) {
        return contractRepository.save(contract);
    }

    public Contract findById(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found with id: " + id));
    }

    public List<Contract> findAll() {
        return contractRepository.findAll();
    }

    public Contract update(Long id, Contract contractDetails) {
        Contract contract = findById(id);
        
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
        Contract contract = findById(id);
        contractRepository.delete(contract);
    }
}
