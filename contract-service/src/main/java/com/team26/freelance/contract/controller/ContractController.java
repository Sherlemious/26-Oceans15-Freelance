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
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.UserContractSummaryDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserContractSummaryDTO> getUserContractSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(contractService.getUserContractSummary(userId));
    }

    @GetMapping("/user/{userId}/active-count")
    public ResponseEntity<Integer> getActiveContractCountForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(contractService.getActiveContractCountForUser(userId));
    }

    @GetMapping("/user/{userId}/completed-count")
    public ResponseEntity<Long> getCompletedContractCountForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(contractService.getCompletedContractCountForUser(userId));
    }

    @GetMapping("/job/{jobId}/active-count")
    public ResponseEntity<Integer> getActiveContractCountForJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(contractService.getActiveContractCountForJob(jobId));
    }

    @GetMapping("/proposal/{proposalId}/active")
    public ResponseEntity<ContractDTO> getActiveContractForProposal(@PathVariable Long proposalId) {
        return ResponseEntity.ok(contractService.getActiveContractForProposal(proposalId));
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
            if (startTime != null) start = LocalDateTime.parse(startTime).atZone(ZoneId.of("UTC")).toInstant();
            if (endTime != null) end = LocalDateTime.parse(endTime).atZone(ZoneId.of("UTC")).toInstant();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format");
        }
        return ResponseEntity.ok(contractService.getContractMilestoneTimeline(id, start, end));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ContractDTO> getContractById(@PathVariable Long contractId) {
        return ResponseEntity.ok(contractService.getContractDtoById(contractId));
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
