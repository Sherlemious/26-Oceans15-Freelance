package com.team26.freelance.proposal.service;

import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.MongoEvent;
import com.team26.freelance.proposal.observer.ProposalEventSubject;
import com.team26.freelance.proposal.adapter.MongoDocumentAdapter;
import com.team26.freelance.proposal.dto.CreateProposalDTO;
import com.team26.freelance.proposal.dto.FeeEstimateDTO;
import com.team26.freelance.proposal.dto.ProposalDetailsDTO;
import com.team26.freelance.proposal.dto.ProposalDetailsDTOBuilder;
import com.team26.freelance.proposal.dto.ProposalMilestoneDTO;
import com.team26.freelance.proposal.dto.UpdateProposalDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDashboardDTO;
import com.team26.freelance.proposal.model.MilestoneStatus;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.model.ProposalStatus;
import com.team26.freelance.proposal.model.ProposalEvent;
import com.team26.freelance.proposal.repository.Neo4jInteractionRepository;
import com.team26.freelance.proposal.repository.ProposalEventRepository;
import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;
import com.team26.freelance.proposal.repository.ProposalRepository;

import org.springframework.cache.annotation.Cacheable;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ProposalService {

    private final Neo4jInteractionRepository neo4jInteractionRepository;
    private final ProposalEventSubject eventSubject;
    private final ProposalRepository proposalRepository;
    private final ProposalMilestoneRepository milestoneRepository;
    private final ProposalCacheEvictionService cacheEvictionService;
    private static final String VALID_KEY_REGEX = "^[a-zA-Z0-9_]+$";
    private static final String DASHBOARD_CACHE_KEY = "proposal-service::S3-F10";

    @Value("${cache.ttl.analytics:600}")
    private long cacheTtlSeconds;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProposalEventRepository proposalEventRepository;
    private final MongoDocumentAdapter mongoDocumentAdapter;


    // MERGED CONSTRUCTOR
    public ProposalService(ProposalRepository proposalRepository,
                           ProposalMilestoneRepository milestoneRepository,
                           ProposalCacheEvictionService cacheEvictionService,
                           RedisTemplate<String, Object> redisTemplate,
                           ProposalEventRepository proposalEventRepository,
                           MongoDocumentAdapter mongoDocumentAdapter,
                           ProposalEventSubject eventSubject, Neo4jInteractionRepository neo4jInteractionRepository) {
        this.proposalRepository = proposalRepository;
        this.milestoneRepository = milestoneRepository;
        this.cacheEvictionService = cacheEvictionService;
        this.redisTemplate = redisTemplate;
        this.proposalEventRepository = proposalEventRepository;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.eventSubject = eventSubject;
        this.neo4jInteractionRepository = neo4jInteractionRepository;
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

    // MERGED FEE ESTIMATE (Keeps CC-3 Cache, Uses Main Builder)
    @Cacheable(value = "proposal-service::S3-F3", key = "#bidAmount + '-' + #estimatedDays")
    public FeeEstimateDTO estimateFee(double bidAmount, int estimatedDays) {
        if (bidAmount <= 0 || estimatedDays < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bidAmount must be positive and estimatedDays must be zero or positive");
        }

        double feePercentage = (estimatedDays <= 5) ? 20.0 : (estimatedDays <= 15) ? 15.0 : 10.0;
        double platformFee = bidAmount * feePercentage / 100;
        double freelancerPayout = bidAmount - platformFee;
        double estimatedDailyRate = freelancerPayout; // Ensures builder doesn't break

        return FeeEstimateDTO.builder()
                .withBidAmount(bidAmount)
                .withPlatformFee(platformFee)
                .withFreelancerPayout(freelancerPayout)
                .withFeePercentage(feePercentage)
                .withEstimatedDailyRate(estimatedDailyRate)
                .build();
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

        eventSubject.notifyObservers("PROPOSAL_COMPLETED", saved);

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

    // MERGED PROPOSAL DETAILS (Keeps CC-3 Cache, Uses Main Builder)
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

        return ProposalDetailsDTOBuilder.builder()
                .withProposalId(proposal.getId())
                .withJobId(proposal.getJobId())
                .withFreelancerId(proposal.getFreelancerId())
                .withStatus(proposal.getStatus())
                .withBidAmount(proposal.getBidAmount())
                .withMetadata(proposal.getMetadata())
                .withMilestones(milestoneDTOs)
                .withTotalMilestones(totalMilestones)
                .withCompletedMilestones(completedMilestones)
                .build();
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
        double acceptanceRate = (total == 0) ? 0.0 : ((double) accepted / total);

        return ProposalAnalyticsDTO.builder()
                .withTotalProposals(total)
                .withAcceptedProposals(accepted)
                .withRejectedProposals(rejected)
                .withTotalBidValue(totalBid)
                .withAverageBid(averageBid)
                .withAcceptanceRate(acceptanceRate)
                .build();
    }

    // ── S3-F10 ─────────────────────────────────────────────────────────────

    public ProposalAnalyticsDashboardDTO getProposalAnalyticsDashboard(
            LocalDate startDate, LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must not be after endDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59, 999_000_000);

        // Build cache key including date params
        String cacheKey = DASHBOARD_CACHE_KEY + "::" + startDate.toString() + "_" + endDate.toString();

        // Check Redis cache first
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                // Log ANALYTICS_VIEWED even on cache hit
                logAnalyticsViewedEvent(startDate, endDate);
                return (ProposalAnalyticsDashboardDTO) cached;
            }
        } catch (Exception e) {
            // Redis soft dependency — continue if unavailable
        }

        // Aggregate stats from PostgreSQL
        List<Object[]> statsList = proposalRepository.getAggregateStats(start, end);
        Object[] stats = statsList.isEmpty() ? new Object[4] : statsList.get(0);
        long total = stats[0] != null ? ((Number) stats[0]).longValue() : 0L;
        double avgBid = stats[1] != null ? ((Number) stats[1]).doubleValue() : 0.0;
        double avgDays = stats[2] != null ? ((Number) stats[2]).doubleValue() : 0.0;
        long accepted = stats[3] != null ? ((Number) stats[3]).longValue() : 0L;
        double acceptanceRate = total > 0 ? (double) accepted / total : 0.0;

        // Status breakdown
        List<Object[]> statusCounts = proposalRepository.countByStatusInRange(start, end);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : statusCounts) {
            String status = row[0].toString();
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
        }
        // Build DTO using Builder pattern
        ProposalAnalyticsDashboardDTO dto = ProposalAnalyticsDashboardDTO.builder()
                .withTotalProposals(total)
                .withAcceptanceRate(acceptanceRate)
                .withAverageBidAmount(avgBid)
                .withAverageEstimatedDays(avgDays)
                .withProposalsByStatus(byStatus)
                .build();

        ProposalAnalyticsDashboardDTO finalDto = dto;

        try {
            Document snapshot = new Document()
                    .append("totalProposals", dto.getTotalProposals())
                    .append("acceptanceRate", dto.getAcceptanceRate())
                    .append("averageBidAmount", dto.getAverageBidAmount())
                    .append("averageEstimatedDays", dto.getAverageEstimatedDays())
                    .append("proposalsByStatus", dto.getProposalsByStatus());
            ProposalAnalyticsDashboardDTO adapted = mongoDocumentAdapter.adapt(snapshot);
            redisTemplate.opsForValue().set(cacheKey, adapted, Duration.ofSeconds(cacheTtlSeconds));
            finalDto = adapted;
        } catch (Exception e) {
            // Redis soft dependency
        }
        logAnalyticsViewedEvent(startDate, endDate);
        return finalDto;
    }

    private void logAnalyticsViewedEvent(LocalDate startDate, LocalDate endDate) {
        try {
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("action", "ANALYTICS_VIEWED");
            params.put("startDate", startDate.toString());
            params.put("endDate", endDate.toString());

            // Create common event via Factory
            MongoEvent event = EventFactory.createEvent(EventType.PROPOSAL, params);

            // Map it to the local Database Entity
            com.team26.freelance.proposal.model.ProposalEvent dbEntity =
                    new com.team26.freelance.proposal.model.ProposalEvent(
                            null,
                            event.getAction(),
                            event.getTimestamp(),
                            event.getDetails()
                    );

            proposalEventRepository.save(dbEntity);
        } catch (Exception e) {
            System.err.println("WARN: Failed to log ANALYTICS_VIEWED event: " + e.getMessage());
        }
    }

    // Call this on any proposal write to invalidate dashboard cache
    public void invalidateDashboardCache() {
        try {
            Set<String> keys = redisTemplate.keys("proposal-service::S3-F10::*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // Redis soft dependency
        }
    }

    @Transactional
    public String recordInteraction(@NonNull Long proposalId) {
        Proposal proposal = getProposalById(proposalId);

        // a) Validate Status
        if (proposal.getStatus() != ProposalStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposal must be in SUBMITTED status to record interaction");
        }

        // b) Idempotency check (Neo4j)
        boolean alreadyRecorded = neo4jInteractionRepository.isInteractionRecorded(
                proposal.getFreelancerId(), proposal.getJobId(), proposalId);

        if (alreadyRecorded) {
            return "Interaction already recorded in Neo4j (Idempotent)";
        }

        // c) Fetch missing data natively from Postgres
        String freelancerName = proposalRepository.findFreelancerNameByIdNative(proposal.getFreelancerId());
        if (freelancerName == null) freelancerName = "Unknown Freelancer";

        List<Object[]> jobDetailsList = proposalRepository.findJobDetailsByIdNative(proposal.getJobId());
        String jobTitle = "Unknown Job";
        String jobCategory = "OTHER";
        if (jobDetailsList != null && !jobDetailsList.isEmpty()) {
            Object[] jobDetails = jobDetailsList.get(0);
            jobTitle = jobDetails[0] != null ? jobDetails[0].toString() : "Unknown Job";
            jobCategory = jobDetails[1] != null ? jobDetails[1].toString() : "OTHER";
        }

        // d) Mutate Neo4j Graph
        neo4jInteractionRepository.recordInteraction(
                proposal.getFreelancerId(), freelancerName,
                proposal.getJobId(), jobTitle, jobCategory,
                proposalId
        );

        // e) Log Mongo Event via Observer (Provides proposalId, freelancerId, jobId automatically)
        eventSubject.notifyObservers("INTERACTION_RECORDED", proposal);

        // f) Invalidate S3-F12 Cache
        cacheEvictionService.evictS3F12Recommendations();

        return "Interaction successfully recorded";
    }

}