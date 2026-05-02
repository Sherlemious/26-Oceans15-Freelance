package com.team26.freelance.proposal.service;

import com.team26.freelance.proposal.dto.CreateProposalDTO;
import com.team26.freelance.proposal.dto.FeeEstimateDTO;
import com.team26.freelance.proposal.dto.ProposalDetailsDTO;
import com.team26.freelance.proposal.dto.ProposalMilestoneDTO;
import com.team26.freelance.proposal.dto.UpdateProposalDTO;
import com.team26.freelance.proposal.model.MilestoneStatus;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDTO;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.model.ProposalStatus;
import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;
import com.team26.freelance.proposal.repository.ProposalRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final ProposalMilestoneRepository milestoneRepository;
    private final ProposalCacheEvictionService cacheEvictionService;
    private static final String VALID_KEY_REGEX = "^[a-zA-Z0-9_]+$";

    public ProposalService(ProposalRepository proposalRepository,
                           ProposalMilestoneRepository milestoneRepository,
                           ProposalCacheEvictionService cacheEvictionService) {
        this.proposalRepository = proposalRepository;
        this.milestoneRepository = milestoneRepository;
        this.cacheEvictionService = cacheEvictionService;
    }

    // ── CRUD (Reads Cached, Writes Evict) ──────────────────────────────────

    public List<Proposal> getAllProposals() {
        return proposalRepository.findAll(); // Intentionally NOT cached per M2 Spec 4.4.2
    }

    @Cacheable(value = "proposal-service::proposal", key = "#id")
    public Proposal getProposalById(@NonNull Long id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
    }

    public Proposal createProposal(CreateProposalDTO request) {
        Proposal proposal = new Proposal();
        proposal.setJobId(request.jobId());
        proposal.setFreelancerId(request.freelancerId());
        proposal.setCoverLetter(request.coverLetter());
        proposal.setBidAmount(request.bidAmount());
        proposal.setEstimatedDays(request.estimatedDays());
        proposal.setMetadata(request.metadata());

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        return saved;
    }

    public Proposal updateProposal(@NonNull Long id, UpdateProposalDTO updated) {
        Proposal existing = getProposalById(id);
        existing.setCoverLetter(updated.coverLetter());
        existing.setBidAmount(updated.bidAmount());
        existing.setEstimatedDays(updated.estimatedDays());
        if (updated.status() != null) {
            existing.setStatus(updated.status());
        }
        if (updated.metadata() != null) {
            existing.setMetadata(updated.metadata());
        }

        Proposal saved = proposalRepository.save(existing);
        cacheEvictionService.evictProposalCaches(saved.getId());
        return saved;
    }

    public void deleteProposal(@NonNull Long id) {
        getProposalById(id);
        proposalRepository.deleteById(id);
        cacheEvictionService.evictProposalCaches(id);
    }

    // ── Features (Reads Cached, Writes Evict) ─────────────────────────────

    @Cacheable(value = "proposal-service::S3-F1", key = "(#status != null ? #status : 'ALL') + '-' + (#startDate != null ? #startDate.toString() : 'NONE') + '-' + (#endDate != null ? #endDate.toString() : 'NONE')")
    public List<Proposal> searchByStatusAndDateRange(String status, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
            }
            start = startDate.atStartOfDay();
            end = endDate.atTime(23, 59, 59);
        }
        return status == null ? proposalRepository.searchByDateRange(start, end)
                : startDate == null && endDate == null ? proposalRepository.searchByStatus(status)
                : proposalRepository.searchByStatusAndDateRange(status, start, end);
    }

    private void validateFreelancer(Long freelancerId) {
        String role = proposalRepository.findFreelancerRole(freelancerId);
        if (role == null || !role.equalsIgnoreCase("FREELANCER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid freelancer ID or user is not a freelancer");
        }
    }

    @Transactional
    public Proposal acceptProposal(@NonNull Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() == ProposalStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposal is already accepted");
        }
        if (proposal.getStatus() != ProposalStatus.SUBMITTED && proposal.getStatus() != ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposal must be SUBMITTED or SHORTLISTED to be accepted");
        }

        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setAcceptedAt(LocalDateTime.now());
        proposalRepository.updateJobStatusToInProgress(proposal.getJobId());
        proposalRepository.insertContractFromProposal(proposalId);

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        return saved;
    }

    @Cacheable(value = "proposal-service::S3-F3", key = "#bidAmount + '-' + #competingProposals")
    public FeeEstimateDTO estimateFee(double bidAmount, int competingProposals) {
        if (bidAmount <= 0 || competingProposals < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bidAmount must be positive and competingProposals must be zero or positive");
        }

        double feePercentage = (competingProposals <= 5) ? 20.0 : (competingProposals <= 15) ? 15.0 : 10.0;
        double platformFee = bidAmount * feePercentage / 100;
        double freelancerPayout = bidAmount - platformFee;

        return new FeeEstimateDTO(bidAmount, platformFee, freelancerPayout, feePercentage, freelancerPayout);
    }

    @Transactional
    public Proposal completeProposalContract(@NonNull Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() != ProposalStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposal status must be ACCEPTED to complete work");
        }

        Long activeContractId = proposalRepository.findActiveContractIdByProposalId(proposalId);
        if (activeContractId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No ACTIVE contract found for this proposal");
        }

        proposalRepository.markContractAsCompleted(activeContractId);
        proposalRepository.updateJobStatusToClosed(proposal.getJobId());
        proposalRepository.insertPendingPayout(activeContractId, proposal.getFreelancerId(), proposal.getBidAmount());

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        return saved;
    }

    @Transactional
    public Proposal withdrawProposal(@NonNull Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() != ProposalStatus.SUBMITTED && proposal.getStatus() != ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only SUBMITTED or SHORTLISTED proposals can be withdrawn");
        }

        proposal.setStatus(ProposalStatus.WITHDRAWN);

        if (proposal.getJobId() != null) {
            int activeProposals = proposalRepository.countActiveProposals(proposal.getJobId());
            if (activeProposals == 0) {
                proposalRepository.reopenJob(proposal.getJobId());
            }
        }

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        return saved;
    }

    @Transactional
    public Proposal addMilestoneToProposal(@NonNull Long proposalId, List<ProposalMilestone> milestones) {
        Proposal proposal = getProposalById(proposalId);

        if (proposal.getStatus() != ProposalStatus.SUBMITTED && proposal.getStatus() != ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestones can only be added to SUBMITTED or SHORTLISTED proposals");
        }
        if (milestones == null || milestones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one milestone is required");
        }

        int milestoneOrder = milestoneRepository.findMaxMilestoneOrderByProposalId(proposalId);
        double currentMilestoneSum = milestoneRepository.sumAmountsByProposalId(proposalId);
        double newMilestoneSum = milestones.stream().peek(this::validateMilestoneInput).mapToDouble(ProposalMilestone::getAmount).sum();

        if (currentMilestoneSum + newMilestoneSum > proposal.getBidAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total milestone amounts cannot exceed proposal bid amount");
        }

        for (ProposalMilestone milestone : milestones) {
            milestone.setProposal(proposal);
            milestone.setMilestoneOrder(++milestoneOrder);
            milestone.setStatus(MilestoneStatus.PENDING);
            proposal.getProposalMilestones().add(milestone);
        }

        proposal.getProposalMilestones().sort(Comparator.comparing(ProposalMilestone::getMilestoneOrder));

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        return saved;
    }

    private void validateMilestoneInput(ProposalMilestone milestone) {
        if (milestone == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone item cannot be null");
        if (milestone.getTitle() == null || milestone.getTitle().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone title is required");
        if (milestone.getDescription() == null || milestone.getDescription().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone description is required");
        if (milestone.getAmount() == null || milestone.getAmount() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone amount must be greater than 0");
    }

    @Cacheable(value = "proposal-service::S3-F9", key = "#proposalId")
    public ProposalDetailsDTO getProposalDetails(Long proposalId) {
        Proposal proposal = proposalRepository.findByIdWithMilestones(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        List<ProposalMilestone> milestones = proposal.getProposalMilestones().stream().sorted(Comparator.comparing(ProposalMilestone::getMilestoneOrder)).toList();
        int totalMilestones = milestones.size();
        int completedMilestones = (int) milestones.stream().filter(m -> (m.getStatus() == MilestoneStatus.COMPLETED || m.getStatus() == MilestoneStatus.APPROVED)).count();

        List<ProposalMilestoneDTO> milestoneDTOs = milestones.stream()
                .map(m -> new ProposalMilestoneDTO(m.getId(), m.getMilestoneOrder(), m.getTitle(), m.getDescription(), m.getAmount(), m.getStatus(), m.getMetadata()))
                .toList();

        return new ProposalDetailsDTO(proposal.getId(), proposal.getJobId(), proposal.getFreelancerId(), proposal.getStatus(), proposal.getBidAmount(), proposal.getMetadata(), milestoneDTOs, totalMilestones, completedMilestones);
    }

    @Cacheable(value = "proposal-service::S3-F5", key = "#key + '-' + #value")
    public List<Proposal> filterProposalsByMetadata(String key, String value) {
        String normalizedKey = key == null ? null : key.trim();
        String normalizedValue = value == null ? null : value.trim();

        if (normalizedKey == null || normalizedKey.isBlank() || normalizedValue == null || normalizedValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metadata key and value must not be blank");
        }
        if (!normalizedKey.matches(VALID_KEY_REGEX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid metadata key");
        }

        return proposalRepository.findByMetadataField(normalizedKey, normalizedValue);
    }

    @Cacheable(value = "proposal-service::S3-F6", key = "#startDate.toString() + '-' + #endDate.toString()")
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

        double averageBid = (total == 0) ? 0.0 : (totalBid / total);
        double acceptanceRate = (total == 0) ? 0.0 : ((double) accepted / total) * 100.0;

        return new ProposalAnalyticsDTO(total, accepted, rejected, totalBid, averageBid, acceptanceRate);
    }
}