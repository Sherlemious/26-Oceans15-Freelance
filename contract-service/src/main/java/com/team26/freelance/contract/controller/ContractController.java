package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractHistoryService;

    public ContractController(ContractService contractHistoryService) {
        this.contractHistoryService = contractHistoryService;
    }

    @GetMapping("/history")
    public ResponseEntity<List<Contract>> getContractHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) ContractStatus status
    ) {
        List<Contract> contracts = contractHistoryService.getContractHistory(startDate, endDate, status);
        return ResponseEntity.ok(contracts);
    }
}
