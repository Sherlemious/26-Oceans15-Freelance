package com.team26.freelance.proposal.service;

import com.team26.freelance.proposal.dto.FeeEstimateDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDTO;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.model.ProposalStatus;
import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;
import com.team26.freelance.proposal.repository.ProposalRepository;

import org.antlr.v4.runtime.misc.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

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

    public FeeEstimateDTO estimateFee(double bidAmount, int estimatedDays) {
        if (bidAmount <= 0 || estimatedDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "bidAmount and estimatedDays must be positive");
        }

        double lower = bidAmount * 0.8;
        double upper = bidAmount * 1.2;
        int similarCount = proposalRepository.countActiveSimilarProposals(lower, upper);

        double feePercentage;
        if (similarCount <= 5) {
            feePercentage = 20.0;
        } else if (similarCount <= 15) {
            feePercentage = 15.0;
        } else {
            feePercentage = 10.0;
        }

        double platformFee = bidAmount * feePercentage / 100;
        double freelancerPayout = bidAmount - platformFee;
        double estimatedDailyRate = freelancerPayout / estimatedDays;

        return new FeeEstimateDTO(bidAmount, platformFee, freelancerPayout,
                feePercentage, estimatedDailyRate);
    }

    @Transactional
    public Proposal completeProposalContract(Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() != ProposalStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Proposal status must be ACCEPTED to complete work");
        }

        Long activeContractId = proposalRepository.findActiveContractIdByProposalId(proposalId);
        if (activeContractId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No ACTIVE contract found for this proposal");
        }

        proposalRepository.markContractAsCompleted(activeContractId);

        proposalRepository.updateJobStatusToClosed(proposal.getJobId());

        proposalRepository.insertPendingPayout(activeContractId, proposal.getFreelancerId(), proposal.getBidAmount());

        return proposalRepository.save(proposal);
    }

    @Transactional
    public Proposal withdrawProposal(@NonNull Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() != ProposalStatus.SUBMITTED && proposal.getStatus() != ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only SUBMITTED or SHORTLISTED proposals can be withdrawn");
        }

        proposal.setStatus(ProposalStatus.WITHDRAWN);

        if (proposal.getJobId() != null) {
            int activeProposals = proposalRepository.countActiveProposals(proposal.getJobId());
            if (activeProposals == 0) {
                proposalRepository.reopenJob(proposal.getJobId());
            }
        }

        return proposalRepository.save(proposal);
    }

    public List<Proposal> filterProposalsByMetadata(String key, String value) {
        String normalizedKey = key == null ? null : key.trim();
        String normalizedValue = value == null ? null : value.trim();

        if (normalizedKey == null || normalizedKey.isBlank() ||
                normalizedValue == null || normalizedValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata key and value must not be blank");
        }

        return proposalRepository.findByMetadataField(normalizedKey, normalizedValue);
    }

    // ── S3-F6: Proposal Analytics by Time Period ────────────────────────────

    public ProposalAnalyticsDTO getProposalAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }

        List<Object[]> results = proposalRepository.getProposalAnalyticsRawData(startDate, endDate);
        Object[] row = results.get(0);

        long total = ((Number) row[0]).longValue();
        long accepted = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long rejected = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        double totalBid = ((Number) row[3]).doubleValue();

        // Handle edge cases to prevent division by zero
        double averageBid = (total == 0) ? 0.0 : (totalBid / total);
        double acceptanceRate = (total == 0) ? 0.0 : ((double) accepted / total) * 100.0;

        return new ProposalAnalyticsDTO(total, accepted, rejected, totalBid, averageBid, acceptanceRate);
    }

}