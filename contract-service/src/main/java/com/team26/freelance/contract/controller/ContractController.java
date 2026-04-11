package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contract.service.dto.ContractStatusUpdateRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping("/history")
    public ResponseEntity<List<Contract>> getContractHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) ContractStatus status
    ) {
        List<Contract> contracts = contractService.getContractHistory(startDate, endDate, status);
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/metadata/search")
    public ResponseEntity<List<Contract>> searchContractsByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value
    ) {
        return ResponseEntity.ok(contractService.searchByMetadata(key, operator, value));
    }
  
    @PutMapping("/{id}")
    public ResponseEntity<Contract> update(@PathVariable Long id, @RequestBody Contract contractDetails) {
        return ResponseEntity.ok(contractService.update(id, contractDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/batch-status")
    public ResponseEntity<Integer> updateStatuses(@RequestBody List<ContractStatusUpdateRequest> contractUpdates) {
        int updatedCount = contractService.updateStatuses(contractUpdates);
        return ResponseEntity.ok(updatedCount);
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
}
