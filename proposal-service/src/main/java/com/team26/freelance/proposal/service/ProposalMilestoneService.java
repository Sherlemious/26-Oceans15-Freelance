package com.team26.freelance.proposal.service;

import com.team26.freelance.proposal.model.MilestoneStatus;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProposalMilestoneService {

    private final ProposalMilestoneRepository milestoneRepository;

    public ProposalMilestoneService(ProposalMilestoneRepository milestoneRepository) {
        this.milestoneRepository = milestoneRepository;
    }

    public List<ProposalMilestone> getAllMilestones() {
        return milestoneRepository.findAll();
    }

    public ProposalMilestone getMilestoneById(Long id) {
        return milestoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found"));
    }

    public ProposalMilestone createMilestone(ProposalMilestone milestone) {
        if (milestone.getStatus() == null) {
            milestone.setStatus(MilestoneStatus.PENDING);
        }
        return milestoneRepository.save(milestone);
    }

    public ProposalMilestone updateMilestone(Long id, ProposalMilestone updated) {
        ProposalMilestone existing = getMilestoneById(id);
        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setAmount(updated.getAmount());
        existing.setStatus(updated.getStatus());
        existing.setMetadata(updated.getMetadata());
        return milestoneRepository.save(existing);
    }

    public void deleteMilestone(Long id) {
        getMilestoneById(id);
        milestoneRepository.deleteById(id);
    }
}

