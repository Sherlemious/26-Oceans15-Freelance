package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.service.ContractService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping
    public ResponseEntity<Contract> create(@RequestBody Contract contract) {
        return new ResponseEntity<>(contractService.create(contract), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contract> findById(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<Contract>> findAll() {
        return ResponseEntity.ok(contractService.findAll());
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
}
