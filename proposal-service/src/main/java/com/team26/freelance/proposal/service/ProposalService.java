package com.team26.freelance.proposal.service;

import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;
import com.team26.freelance.proposal.repository.ProposalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final ProposalMilestoneRepository milestoneRepository;

    public ProposalService(ProposalRepository proposalRepository,
                           ProposalMilestoneRepository milestoneRepository) {
        this.proposalRepository = proposalRepository;
        this.milestoneRepository = milestoneRepository;
    }

    // ── CRUD ───────────────────────────────────────────────────────────────

    public List<Proposal> getAllProposals() {
        return proposalRepository.findAll();
    }

    public Proposal getProposalById(Long id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
    }

    public Proposal createProposal(Proposal proposal) {
        return proposalRepository.save(proposal);
    }

    public Proposal updateProposal(Long id, Proposal updated) {
        Proposal existing = getProposalById(id);
        existing.setCoverLetter(updated.getCoverLetter());
        existing.setBidAmount(updated.getBidAmount());
        existing.setEstimatedDays(updated.getEstimatedDays());
        existing.setStatus(updated.getStatus());
        existing.setMetadata(updated.getMetadata());
        return proposalRepository.save(existing);
    }

    public void deleteProposal(Long id) {
        getProposalById(id); // throws 404 if missing
        proposalRepository.deleteById(id);
    }

    // ── CRUD for Milestones ────────────────────────────────────────────────

    public List<ProposalMilestone> getAllMilestones() {
        return milestoneRepository.findAll();
    }

    public ProposalMilestone getMilestoneById(Long id) {
        return milestoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found"));
    }

    public ProposalMilestone createMilestone(ProposalMilestone milestone) {
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
  
  
    public List<Proposal> searchByStatusAndDateRange(String status, LocalDateTime startDate, LocalDateTime endDate) {
        return proposalRepository.searchByStatusAndDateRange(status, startDate, endDate);
    }

    @Transactional
    public Proposal acceptProposal(Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() != ProposalStatus.SUBMITTED
                && proposal.getStatus() != ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Proposal must be SUBMITTED or SHORTLISTED to be accepted");
        }

        String role = proposalRepository.findFreelancerRole(proposal.getFreelancerId());
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer user not found");
        }
        if (!role.equals("FREELANCER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a FREELANCER");
        }

        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setAcceptedAt(LocalDateTime.now());
        proposalRepository.updateJobStatusToInProgress(proposal.getJobId());
        proposalRepository.insertContractFromProposal(proposalId);

        return proposalRepository.save(proposal);
    }
}