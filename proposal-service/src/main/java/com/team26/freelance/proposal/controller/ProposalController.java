package com.team26.freelance.proposal.controller;

import com.team26.freelance.proposal.dto.*;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.service.ProposalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    // ── Proposal CRUD ──────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Proposal>> getAllProposals() {
        return ResponseEntity.ok(proposalService.getAllProposals());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Proposal> getProposalById(@NonNull @PathVariable Long id) {
        return ResponseEntity.ok(proposalService.getProposalById(id));
    }

    @PostMapping
    public ResponseEntity<Proposal> createProposal(@Valid @RequestBody CreateProposalDTO request) {
        return ResponseEntity.status(201).body(proposalService.createProposal(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Proposal> updateProposal(@NonNull @PathVariable Long id,
            @Valid @RequestBody UpdateProposalDTO proposal) {
        return ResponseEntity.ok(proposalService.updateProposal(id, proposal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProposal(@NonNull @PathVariable Long id) {
        proposalService.deleteProposal(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Proposal>> searchProposals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(proposalService.searchByStatusAndDateRange(status, startDate, endDate));
    }

    @PutMapping("/{proposalId}/accept")
    public ResponseEntity<Proposal> acceptProposal(@NonNull @PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.acceptProposal(proposalId));
    }

    @PostMapping("/estimate")
    public ResponseEntity<FeeEstimateDTO> estimateFee(@Valid @RequestBody FeeEstimateRequest request) {
        return ResponseEntity.ok(proposalService.estimateFee(
                request.getBidAmount(), request.getCompetingProposals()));
    }

    // ── S3-F4: Complete Proposal's Contract ─────────────────────────────────

    @PutMapping("/{id}/complete")
    public ResponseEntity<Proposal> completeProposalContract(@NonNull @PathVariable Long id) {
        Proposal completedProposal = proposalService.completeProposalContract(id);
        return ResponseEntity.ok(completedProposal);
    }

    @PutMapping("/{id}/withdraw")
    public ResponseEntity<Proposal> withdrawProposal(@NonNull @PathVariable Long id) {
        Proposal withdrawnProposal = proposalService.withdrawProposal(id);
        return ResponseEntity.ok(withdrawnProposal);
    }

    @PostMapping("/{proposalId}/milestones")
    public ResponseEntity<Proposal> addMilestonesToProposal(@NonNull @PathVariable Long proposalId,
            @RequestBody List<ProposalMilestone> milestones) {
        Proposal updatedProposal = proposalService.addMilestoneToProposal(proposalId, milestones);
        return ResponseEntity.ok(updatedProposal);
    }

    @GetMapping("/{proposalId}/details")
    public ResponseEntity<ProposalDetailsDTO> getProposalDetails(@NonNull @PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.getProposalDetails(proposalId));
    }

    // ── S3-F5: Filter Proposals by Metadata ─────────────────────────────────

    @GetMapping("/metadata/search")
    public ResponseEntity<List<Proposal>> searchByMetadata(
            @NotBlank @RequestParam(required = true) String key,
            @NotBlank @RequestParam(required = true) String value) {

        List<Proposal> results = proposalService.filterProposalsByMetadata(key, value);
        return ResponseEntity.ok(results);
    }

    // ── S3-F6: Proposal Analytics by Time Period ────────────────────────────

    @GetMapping("/analytics")
    public ResponseEntity<ProposalAnalyticsDTO> getAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Convert to timestamp: start at 00:00:00, end at 23:59:59
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        ProposalAnalyticsDTO report = proposalService.getProposalAnalytics(start, end);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/analytics/dashboard")
    public ResponseEntity<ProposalAnalyticsDashboardDTO> getAnalyticsDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(
                proposalService.getProposalAnalyticsDashboard(startDate, endDate));
    }

}