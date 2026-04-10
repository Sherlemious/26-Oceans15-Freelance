package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.model.Contract;

import com.team26.freelance.contract.service.ContractService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    // GET /api/contracts
    @GetMapping
    public ResponseEntity<List<Contract>> getAllContracts() {
        return ResponseEntity.ok(contractService.getAllContracts());
    }

    // GET /api/contracts/{id}  ← used by job-service via RestTemplate
    @GetMapping("/{id}")
    public ResponseEntity<Contract> getContractById(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getContractById(id));
    }

    // POST /api/contracts
    @PostMapping
    public ResponseEntity<Contract> createContract(@RequestBody Contract contract) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(contract));
    }

    @PutMapping("/{contractId}/progress")
    public ResponseEntity<Contract> updateContractProgress(@PathVariable Long contractId,
                                                           @RequestBody Map<String, Object> incomingMetadata) {
        return ResponseEntity.ok(contractService.updateContractProgress(contractId, incomingMetadata));
    }
}
