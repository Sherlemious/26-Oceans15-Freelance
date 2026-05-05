package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.dto.BatchStatusUpdateRequestDTO;
import com.team26.freelance.contract.dto.ContractAnalyticsDTO;
import com.team26.freelance.contract.dto.ContractMilestoneDTO;
import com.team26.freelance.contract.dto.ContractDateRangeDTO;
import com.team26.freelance.contract.dto.ContractSummaryDTO;
import com.team26.freelance.contract.dto.MilestoneTrackingRequest;
import com.team26.freelance.contract.dto.MilestoneTrackingResponse;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractAnalyticsService;
import com.team26.freelance.contract.service.ContractService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;
    private final ContractAnalyticsService contractAnalyticsService;

    public ContractController(ContractService contractService, ContractAnalyticsService contractAnalyticsService) {
        this.contractService = contractService;
        this.contractAnalyticsService = contractAnalyticsService;
    }

    @GetMapping("/history")
    public ResponseEntity<List<ContractDateRangeDTO>> getContractHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) ContractStatus status) {
        List<ContractDateRangeDTO> contracts = contractService.getContractHistory(startDate, endDate, status);
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/metadata/search")
    public ResponseEntity<List<Contract>> searchContractsByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value) {
        return ResponseEntity.ok(contractService.searchByMetadata(key, operator, value));
    }

    @PutMapping("/batch-status")
    public ResponseEntity<Integer> updateStatuses(@RequestBody @Valid List<BatchStatusUpdateRequestDTO> request) {
        int updatedCount = contractService.updateStatusesRaw(request);
        return ResponseEntity.ok(updatedCount);
    }

    // GET /api/contracts
    @GetMapping
    public ResponseEntity<List<Contract>> getAllContracts() {
        return ResponseEntity.ok(contractService.getAllContracts());
    }

    @GetMapping("/analytics")
    public ResponseEntity<ContractAnalyticsDTO> getContractAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        contractAnalyticsService.recordAnalyticsViewed(startDate, endDate);
        return ResponseEntity.ok(contractAnalyticsService.getAnalytics(startDate, endDate));
    }

    // GET /api/contracts/user/{userId}/active
    @GetMapping("/user/{userId}/active")
    public ResponseEntity<Contract> getActiveContractForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(contractService.getActiveContractForUser(userId));
    }

    // POST /api/contracts
    @PostMapping
    public ResponseEntity<Contract> createContract(@RequestBody Contract contract) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(contract));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ContractSummaryDTO>> searchContracts(@RequestParam Double minAmount,
            @RequestParam Double maxAmount,
            @RequestParam(required = false) String status) {
        if (minAmount < 0 || maxAmount < minAmount) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                contractService.findContractsByBudgetRangeWithFreelancerInfo(minAmount, maxAmount, status));

    }

    @PutMapping("/{contractId}/progress")
    public ResponseEntity<Contract> updateContractProgress(@PathVariable Long contractId,
            @RequestBody Map<String, Object> incomingMetadata) {
        return ResponseEntity.ok(contractService.updateContractProgress(contractId, incomingMetadata));
    }

    @PostMapping("/{id}/milestones/track")
    public ResponseEntity<MilestoneTrackingResponse> trackMilestone(@PathVariable Long id,
            @RequestBody @Valid MilestoneTrackingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.trackMilestone(id, request));
    }

    @GetMapping("/{id}/milestones/timeline")
    public ResponseEntity<List<ContractMilestoneDTO>> getContractMilestoneTimeline(
            @PathVariable Long id,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Instant start = null;
        Instant end = null;
        try {
            if (startTime != null) start = Instant.parse(startTime);
            if (endTime != null) end = Instant.parse(endTime);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format");
        }
        return ResponseEntity.ok(contractService.getContractMilestoneTimeline(id, start, end));
    }

    // GET /api/contracts/{id} ← used by job-service via RestTemplate
    @GetMapping("/{id}")
    public ResponseEntity<Contract> getContractById(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getContractById(id));
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
