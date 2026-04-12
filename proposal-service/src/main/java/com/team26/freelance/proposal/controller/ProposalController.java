package com.team26.freelance.proposal.controller;

import com.team26.freelance.proposal.dto.FeeEstimateDTO;
import com.team26.freelance.proposal.dto.FeeEstimateRequest;
import com.team26.freelance.proposal.dto.ProposalDetailsDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDTO;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.service.ProposalService;
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
    public ResponseEntity<Proposal> getProposalById(@PathVariable Long id) {
        return ResponseEntity.ok(proposalService.getProposalById(id));
    }

    @PostMapping
    public ResponseEntity<Proposal> createProposal(@RequestBody Proposal proposal) {
        return ResponseEntity.status(201).body(proposalService.createProposal(proposal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Proposal> updateProposal(@PathVariable Long id,
            @RequestBody Proposal proposal) {
        return ResponseEntity.ok(proposalService.updateProposal(id, proposal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProposal(@PathVariable Long id) {
        proposalService.deleteProposal(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Proposal>> searchProposals(
            @RequestParam(required = false) String status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        return ResponseEntity.ok(proposalService.searchByStatusAndDateRange(status, start, end));
    }

    @PutMapping("/{proposalId}/accept")
    public ResponseEntity<Proposal> acceptProposal(@PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.acceptProposal(proposalId));
    }

    @PostMapping("/estimate")
    public ResponseEntity<FeeEstimateDTO> estimateFee(
            @RequestParam(required = false) Double bidAmount,
            @RequestParam(required = false) Integer estimatedDays,
            @RequestBody(required = false) FeeEstimateRequest request) {

        double finalBidAmount = bidAmount != null ? bidAmount : (request != null ? request.getBidAmount() : 0);
        int finalDays = estimatedDays != null ? estimatedDays : (request != null ? request.getEstimatedDays() : 0);

        return ResponseEntity.ok(proposalService.estimateFee(finalBidAmount, finalDays));
    }

    // ── S3-F4: Complete Proposal's Contract ─────────────────────────────────

    @PutMapping("/{id}/complete")
    public ResponseEntity<Proposal> completeProposalContract(@PathVariable Long id) {
        Proposal completedProposal = proposalService.completeProposalContract(id);
        return ResponseEntity.ok(completedProposal);
    }

    @PutMapping("/{id}/withdraw")
    public ResponseEntity<Proposal> withdrawProposal(@NonNull @PathVariable Long id) {
        Proposal withdrawnProposal = proposalService.withdrawProposal(id);
        return ResponseEntity.ok(withdrawnProposal);
    }

    @PostMapping("/{proposalId}/milestones")
    public ResponseEntity<Proposal> addMilestonesToProposal(@PathVariable Long proposalId,
            @RequestBody List<ProposalMilestone> milestones) {
        Proposal updatedProposal = proposalService.addMilestoneToProposal(proposalId, milestones);
        return ResponseEntity.ok(updatedProposal);
    }

    @GetMapping("/{proposalId}/details")
    public ResponseEntity<ProposalDetailsDTO> getProposalDetails(@PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.getProposalDetails(proposalId));
    }

    // ── S3-F5: Filter Proposals by Metadata ─────────────────────────────────

    @GetMapping("/metadata/search")
    public ResponseEntity<List<Proposal>> searchByMetadata(
            @RequestParam String key,
            @RequestParam String value) {

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

}