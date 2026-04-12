package com.team26.freelance.proposal.controller;

import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.service.ProposalMilestoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/milestones")
public class ProposalMilestoneController {

    private final ProposalMilestoneService milestoneService;

    public ProposalMilestoneController(ProposalMilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping
    public ResponseEntity<List<ProposalMilestone>> getAllMilestones() {
        return ResponseEntity.ok(milestoneService.getAllMilestones());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProposalMilestone> getMilestoneById(@PathVariable Long id) {
        return ResponseEntity.ok(milestoneService.getMilestoneById(id));
    }

    @PostMapping
    public ResponseEntity<ProposalMilestone> createMilestone(@RequestBody ProposalMilestone milestone) {
        return ResponseEntity.status(201).body(milestoneService.createMilestone(milestone));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProposalMilestone> updateMilestone(@PathVariable Long id,
            @RequestBody ProposalMilestone milestone) {
        return ResponseEntity.ok(milestoneService.updateMilestone(id, milestone));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMilestone(@PathVariable Long id) {
        milestoneService.deleteMilestone(id);
        return ResponseEntity.noContent().build();
    }
}

