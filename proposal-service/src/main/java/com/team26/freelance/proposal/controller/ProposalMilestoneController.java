package com.team26.freelance.proposal.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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

    @GetMapping
    public ResponseEntity<List<ProposalMilestone>> getAllMilestones() {
        return ResponseEntity.ok(proposalMilestoneService.getAllMilestones());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProposalMilestone> getMilestoneById(@NonNull @PathVariable Long id) {
        return ResponseEntity.ok(proposalMilestoneService.getMilestoneById(id));
    }

    @PostMapping("/proposal/{id}")
    public ResponseEntity<ProposalMilestone> createMilestone(@NonNull @PathVariable Long id,
            @RequestBody CreateProposalMilestoneDTO milestone) {
        return ResponseEntity.status(201).body(proposalMilestoneService.createMilestone(id, milestone));
    }

    @PutMapping("/milestones/{id}")
    public ResponseEntity<ProposalMilestone> updateMilestone(@PathVariable Long id,
            @RequestBody ProposalMilestone milestone) {
        return ResponseEntity.ok(proposalMilestoneService.updateMilestone(id, milestone));
    }

    @DeleteMapping("/milestones/{id}")
    public ResponseEntity<Void> deleteMilestone(@PathVariable Long id) {
        proposalMilestoneService.deleteMilestone(id);
        return ResponseEntity.noContent().build();
    }
}
