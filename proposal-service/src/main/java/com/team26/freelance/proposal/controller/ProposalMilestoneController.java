package com.team26.freelance.proposal.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team26.freelance.proposal.dto.CreateProposalMilestoneDTO;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.service.ProposalMilestoneService;

@RestController
@RequestMapping("/api/proposal-milestones")
public class ProposalMilestoneController {

    private final ProposalMilestoneService proposalMilestoneService;

    public ProposalMilestoneController(ProposalMilestoneService proposalMilestoneService) {
        this.proposalMilestoneService = proposalMilestoneService;
    }

    // ── Milestone CRUD ─────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<ProposalMilestone>> getAllMilestones() {
        return ResponseEntity.ok(proposalMilestoneService.getAllMilestones());
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ProposalMilestone> getMilestoneById(@NonNull @PathVariable Long id) {
        return ResponseEntity.ok(proposalMilestoneService.getMilestoneById(id));
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN')")
    @PostMapping("/proposal/{id}")
    public ResponseEntity<ProposalMilestone> createMilestone(@NonNull @PathVariable Long id,
                                                             @Valid @RequestBody CreateProposalMilestoneDTO milestone) {
        return ResponseEntity.status(201).body(proposalMilestoneService.createMilestone(id, milestone));
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProposalMilestone> updateMilestone(@NonNull @PathVariable Long id,
                                                             @RequestBody ProposalMilestone milestone) {
        return ResponseEntity.ok(proposalMilestoneService.updateMilestone(id, milestone));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMilestone(@NonNull @PathVariable Long id) {
        proposalMilestoneService.deleteMilestone(id);
        return ResponseEntity.noContent().build();
    }
}