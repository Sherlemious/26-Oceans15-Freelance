package com.team26.freelance.proposal.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.team26.freelance.proposal.dto.CreateProposalMilestoneDTO;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;

@Service
public class ProposalMilestoneService {

    private ProposalMilestoneRepository milestoneRepository;
    private ProposalService proposalService;
    private ProposalMilestoneRepository proposalMilestoneRepository;

    public ProposalMilestoneService(ProposalMilestoneRepository milestoneRepository, ProposalService proposalService,
            ProposalMilestoneRepository proposalMilestoneRepository) {
        this.milestoneRepository = milestoneRepository;
        this.proposalService = proposalService;
        this.proposalMilestoneRepository = proposalMilestoneRepository;
    }

    // ── CRUD for Milestones ────────────────────────────────────────────────

    public List<ProposalMilestone> getAllMilestones() {
        return milestoneRepository.findAll();
    }

    public ProposalMilestone getMilestoneById(@NonNull Long id) {
        return milestoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found"));
    }

    public ProposalMilestone createMilestone(@NonNull Long id, CreateProposalMilestoneDTO milestone) {
        Proposal proposal = proposalService.getProposalById(id);
        int currentCount = proposalMilestoneRepository.findMaxMilestoneOrderByProposalId(id);
        ProposalMilestone proposalMilestone = new ProposalMilestone();
        proposalMilestone.setTitle(milestone.title());
        proposalMilestone.setDescription(milestone.description());
        proposalMilestone.setAmount(milestone.amount());
        proposalMilestone.setMetadata(milestone.metadata());
        proposalMilestone.setMilestoneOrder(currentCount + 1);
        proposalMilestone.setProposal(proposal);
        return milestoneRepository.save(proposalMilestone);
    }

    public ProposalMilestone updateMilestone(@NonNull Long id, ProposalMilestone updated) {
        ProposalMilestone existing = getMilestoneById(id);
        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setAmount(updated.getAmount());
        existing.setStatus(updated.getStatus());
        existing.setMetadata(updated.getMetadata());
        return milestoneRepository.save(existing);
    }

    public void deleteMilestone(@NonNull Long id) {
        getMilestoneById(id);
        milestoneRepository.deleteById(id);
    }
}
