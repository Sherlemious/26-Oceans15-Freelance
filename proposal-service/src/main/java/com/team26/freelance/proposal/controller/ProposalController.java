package com.team26.freelance.proposal.controller;

import com.team26.freelance.proposal.dto.FeeEstimateDTO;
import com.team26.freelance.proposal.dto.FeeEstimateRequest;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.service.ProposalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // ── Milestone CRUD ─────────────────────────────────────────────────────

    @GetMapping("/milestones")
    public ResponseEntity<List<ProposalMilestone>> getAllMilestones() {
        return ResponseEntity.ok(proposalService.getAllMilestones());
    }

    @GetMapping("/milestones/{id}")
    public ResponseEntity<ProposalMilestone> getMilestoneById(@PathVariable Long id) {
        return ResponseEntity.ok(proposalService.getMilestoneById(id));
    }

    @PostMapping("/milestones")
    public ResponseEntity<ProposalMilestone> createMilestone(@RequestBody ProposalMilestone milestone) {
        return ResponseEntity.status(201).body(proposalService.createMilestone(milestone));
    }

    @PutMapping("/milestones/{id}")
    public ResponseEntity<ProposalMilestone> updateMilestone(@PathVariable Long id,
                                                             @RequestBody ProposalMilestone milestone) {
        return ResponseEntity.ok(proposalService.updateMilestone(id, milestone));
    }

    @DeleteMapping("/milestones/{id}")
    public ResponseEntity<Void> deleteMilestone(@PathVariable Long id) {
        proposalService.deleteMilestone(id);
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
    public ResponseEntity<FeeEstimateDTO> estimateFee(@RequestBody FeeEstimateRequest request) {
        return ResponseEntity.ok(proposalService.estimateFee(
                request.getBidAmount(), request.getEstimatedDays()));
    }

    // ── S3-F4: Complete Proposal's Contract ─────────────────────────────────

    @PutMapping("/{id}/complete")
    public ResponseEntity<Proposal> completeProposalContract(@PathVariable Long id) {
        Proposal completedProposal = proposalService.completeProposalContract(id);
        return ResponseEntity.ok(completedProposal);
    }

}