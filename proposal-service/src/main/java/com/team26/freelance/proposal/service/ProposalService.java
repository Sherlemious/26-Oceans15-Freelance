package com.team26.freelance.proposal.service;

import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.MongoEvent;
import com.team26.freelance.common.event.ProposalEvent;
import com.team26.freelance.proposal.observer.ProposalEventSubject;
import com.team26.freelance.proposal.adapter.MongoDocumentAdapter;
import com.team26.freelance.proposal.adapter.Neo4jRecordAdapter;
import com.team26.freelance.proposal.dto.CreateProposalDTO;
import com.team26.freelance.proposal.dto.FeeEstimateDTO;
import com.team26.freelance.proposal.dto.ProposalDetailsDTO;
import com.team26.freelance.proposal.dto.ProposalDetailsDTOBuilder;
import com.team26.freelance.proposal.dto.ProposalMilestoneDTO;
import com.team26.freelance.proposal.dto.UpdateProposalDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDashboardDTO;
import com.team26.freelance.proposal.dto.JobRecommendationDTO;
import com.team26.freelance.proposal.feign.ContractServiceClient;
import com.team26.freelance.proposal.feign.JobServiceClient;
import com.team26.freelance.proposal.feign.UserServiceClient;
import com.team26.freelance.contracts.dto.JobProposalSummaryDTO;
import com.team26.freelance.contracts.dto.ProposalDTO;
import com.team26.freelance.proposal.model.MilestoneStatus;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.model.ProposalStatus;
import com.team26.freelance.proposal.repository.Neo4jInteractionRepository;
import com.team26.freelance.proposal.repository.ProposalEventRepository;
import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;
import com.team26.freelance.proposal.repository.ProposalRepository;
import org.springframework.data.neo4j.core.Neo4jClient;
import com.team26.freelance.contracts.dto.UserDTO;
import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.contracts.dto.ContractDTO;

import org.springframework.cache.annotation.Cacheable;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import feign.FeignException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ProposalService {

    private static final Logger logger = LoggerFactory.getLogger(ProposalService.class);

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
    private final Neo4jClient neo4jClient;
    private final Neo4jRecordAdapter neo4jRecordAdapter;
    private final ProposalEventPublisher proposalEventPublisher;
    private final UserServiceClient userServiceClient;
    private final JobServiceClient jobServiceClient;
    private final ContractServiceClient contractServiceClient;

    // MERGED CONSTRUCTOR
    public ProposalService(ProposalRepository proposalRepository,
            ProposalMilestoneRepository milestoneRepository,
            ProposalCacheEvictionService cacheEvictionService,
            RedisTemplate<String, Object> redisTemplate,
            ProposalEventRepository proposalEventRepository,
            MongoDocumentAdapter mongoDocumentAdapter,
            ProposalEventSubject eventSubject,
            Neo4jInteractionRepository neo4jInteractionRepository,
            Neo4jClient neo4jClient,
            Neo4jRecordAdapter neo4jRecordAdapter,
            ProposalEventPublisher proposalEventPublisher,
            UserServiceClient userServiceClient,
            JobServiceClient jobServiceClient,
            ContractServiceClient contractServiceClient) {
        this.proposalRepository = proposalRepository;
        this.milestoneRepository = milestoneRepository;
        this.cacheEvictionService = cacheEvictionService;
        this.redisTemplate = redisTemplate;
        this.proposalEventRepository = proposalEventRepository;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.eventSubject = eventSubject;
        this.neo4jInteractionRepository = neo4jInteractionRepository;
        this.neo4jClient = neo4jClient;
        this.neo4jRecordAdapter = neo4jRecordAdapter;
        this.proposalEventPublisher = proposalEventPublisher;
        this.userServiceClient = userServiceClient;
        this.jobServiceClient = jobServiceClient;
        this.contractServiceClient = contractServiceClient;
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

    public ProposalDTO getProposalDtoById(@NonNull Long id) {
        Proposal proposal = getProposalById(id);
        return new ProposalDTO(
                proposal.getId(),
                proposal.getJobId(),
                proposal.getFreelancerId(),
                proposal.getStatus() != null ? proposal.getStatus().name() : null,
                proposal.getBidAmount() != null ? BigDecimal.valueOf(proposal.getBidAmount()) : null,
                proposal.getAcceptedAt());
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
        String normalizedStatus = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();
        ProposalStatus statusEnum = null;
        if (normalizedStatus != null) {
            try {
                statusEnum = ProposalStatus.valueOf(normalizedStatus);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + normalizedStatus);
            }
        }

        LocalDate effectiveStartDate = startDate;
        LocalDate effectiveEndDate = endDate;
        if (effectiveStartDate != null && effectiveEndDate == null) {
            effectiveEndDate = effectiveStartDate;
        } else if (effectiveStartDate == null && effectiveEndDate != null) {
            effectiveStartDate = effectiveEndDate;
        }

        LocalDateTime start = null;
        LocalDateTime end = null;
        if (effectiveStartDate != null && effectiveEndDate != null) {
            if (effectiveStartDate.isAfter(effectiveEndDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
            }
            start = effectiveStartDate.atStartOfDay();
            end = effectiveEndDate.atTime(LocalTime.MAX);
        }

        if (statusEnum != null && start != null && end != null) {
            return proposalRepository.findByStatusAndSubmittedAtBetweenOrderBySubmittedAtDesc(
                    statusEnum, start, end);
        }
        if (statusEnum != null) {
            return proposalRepository.findByStatusOrderBySubmittedAtDesc(statusEnum);
        }
        if (start != null && end != null) {
            return proposalRepository.findBySubmittedAtBetweenOrderBySubmittedAtDesc(start, end);
        }
        return proposalRepository.findAll().stream()
                .filter(proposal -> proposal.getSubmittedAt() != null)
                .sorted(Comparator.comparing(Proposal::getSubmittedAt).reversed())
                .toList();
    }

    private void validateFreelancer(Long freelancerId) {
        try {
            var user = userServiceClient.getUser(freelancerId);
            if (user == null || user.getRole() == null || !"FREELANCER".equalsIgnoreCase(user.getRole())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid freelancer ID or user is not a freelancer");
            }
        } catch (FeignException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid freelancer ID or user is not a freelancer");
        } catch (Exception e) {
            logger.warn("UserService unreachable while validating freelancerId={}", freelancerId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Proposal must be SUBMITTED or SHORTLISTED to be accepted");
        }

        // Feign validation: Ensure freelancer has FREELANCER role
        try {
            var user = userServiceClient.getUser(proposal.getFreelancerId());
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found");
            }
            if (user.getRole() == null || !"FREELANCER".equalsIgnoreCase(user.getRole())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "User is not a freelancer");
            }
        } catch (feign.FeignException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Failed to validate freelancer role for freelancerId={}", proposal.getFreelancerId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to validate freelancer role");
        }

        MDC.put("proposalId", proposalId.toString());
        ProposalStatus oldStatus = proposal.getStatus();
        
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setAcceptedAt(LocalDateTime.now());

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        
        logger.info("Proposal {} transitioning {} -> ACCEPTED", proposalId, oldStatus);

        // Publish proposal.accepted event
        proposalEventPublisher.publishProposalAccepted(
            proposalId,
            proposal.getJobId(),
            proposal.getFreelancerId(),
            java.math.BigDecimal.valueOf(proposal.getBidAmount())
        );

        MDC.remove("proposalId");
        return saved;
    }

    // MERGED FEE ESTIMATE (Keeps CC-3 Cache, Uses Main Builder)
    @Cacheable(value = "proposal-service::S3-F3", key = "#bidAmount + '-' + #estimatedDays")
    public FeeEstimateDTO estimateFee(double bidAmount, int estimatedDays) {
        if (bidAmount <= 0 || estimatedDays < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bidAmount must be positive and estimatedDays must be zero or positive");
        }

        double feePercentageValue = (estimatedDays <= 5) ? 20.0 : (estimatedDays <= 15) ? 15.0 : 10.0;
        double platformFee = bidAmount * feePercentageValue / 100;
        double freelancerPayout = bidAmount - platformFee;
        double estimatedDailyRate = (estimatedDays > 0) ? (bidAmount / estimatedDays) : 0.0;
        double feePercentage = feePercentageValue / 100.0;

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Proposal status must be ACCEPTED to complete work");
        }

        // Feign pre-checks
        Long activeContractId = null;
        try {
            // Check job status is not CLOSED
            var job = jobServiceClient.getJob(proposal.getJobId());
            if (job != null && "CLOSED".equals(job.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job is already closed");
            }

            // Check freelancer is ACTIVE
            var user = userServiceClient.getUser(proposal.getFreelancerId());
            if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Freelancer is not active");
            }

            // Check active contract exists
            var contract = contractServiceClient.getActiveContractForProposal(proposalId);
            if (contract == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active contract found for this proposal");
            }
            activeContractId = contract.getId();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Failed to validate pre-conditions for proposalId={}", proposalId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to validate pre-conditions");
        }

        MDC.put("proposalId", proposalId.toString());
        ProposalStatus oldStatus = proposal.getStatus();
        
        // Set status to COMPLETING (saga trigger)
        proposal.setStatus(ProposalStatus.COMPLETING);

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        
        logger.info("Proposal {} transitioning {} -> COMPLETING (saga trigger)", proposalId, oldStatus);

        // Publish proposal.completed event (saga trigger)
        proposalEventPublisher.publishProposalCompleted(
            proposalId,
            proposal.getJobId(),
            proposal.getFreelancerId(),
            activeContractId,
            java.math.BigDecimal.valueOf(proposal.getBidAmount())
        );

        MDC.remove("proposalId");
        return saved;
    }

    @Transactional
    public Proposal withdrawProposal(@NonNull Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        if (proposal.getStatus() != ProposalStatus.SUBMITTED && proposal.getStatus() != ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only SUBMITTED or SHORTLISTED proposals can be withdrawn");
        }

        MDC.put("proposalId", proposalId.toString());
        ProposalStatus oldStatus = proposal.getStatus();
        
        proposal.setStatus(ProposalStatus.WITHDRAWN);

        Proposal saved = proposalRepository.save(proposal);
        cacheEvictionService.evictProposalCaches(saved.getId());
        
        logger.info("Proposal {} transitioning {} -> WITHDRAWN", proposalId, oldStatus);

        // Publish proposal.withdrawn event
        proposalEventPublisher.publishProposalWithdrawn(
            proposalId,
            proposal.getJobId(),
            proposal.getFreelancerId()
        );

        MDC.remove("proposalId");
        return saved;
    }

    @Transactional
    public Proposal addMilestoneToProposal(@NonNull Long proposalId, List<ProposalMilestone> milestones) {
        Proposal proposal = getProposalById(proposalId);

        if (proposal.getStatus() != ProposalStatus.SUBMITTED && proposal.getStatus() != ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Milestones can only be added to SUBMITTED or SHORTLISTED proposals");
        }
        if (milestones == null || milestones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one milestone is required");
        }

        int milestoneOrder = milestoneRepository.findMaxMilestoneOrderByProposalId(proposalId);
        double currentMilestoneSum = milestoneRepository.sumAmountsByProposalId(proposalId);
        double newMilestoneSum = milestones.stream().peek(this::validateMilestoneInput)
                .mapToDouble(ProposalMilestone::getAmount).sum();

        if (currentMilestoneSum + newMilestoneSum > proposal.getBidAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total milestone amounts cannot exceed proposal bid amount");
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
        if (milestone == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone item cannot be null");
        if (milestone.getTitle() == null || milestone.getTitle().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone title is required");
        if (milestone.getDescription() == null || milestone.getDescription().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone description is required");
        if (milestone.getAmount() == null || milestone.getAmount() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Milestone amount must be greater than 0");
    }

    // MERGED PROPOSAL DETAILS (Keeps CC-3 Cache, Uses Main Builder)
    @Cacheable(value = "proposal-service::S3-F9", key = "#proposalId")
    public ProposalDetailsDTO getProposalDetails(Long proposalId) {
        Proposal proposal = proposalRepository.findByIdWithMilestones(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));

        List<ProposalMilestone> milestones = proposal.getProposalMilestones().stream()
                .sorted(Comparator.comparing(ProposalMilestone::getMilestoneOrder)).toList();
        int totalMilestones = milestones.size();
        int completedMilestones = (int) milestones.stream()
                .filter(m -> (m.getStatus() == MilestoneStatus.COMPLETED || m.getStatus() == MilestoneStatus.APPROVED))
                .count();

        List<ProposalMilestoneDTO> milestoneDTOs = milestones.stream()
                .map(m -> new ProposalMilestoneDTO(m.getId(), m.getMilestoneOrder(), m.getTitle(), m.getDescription(),
                        m.getAmount(), m.getStatus(), m.getMetadata()))
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

        try {
            String jsonFilter = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of(normalizedKey, normalizedValue));
            List<Proposal> results = proposalRepository.findByMetadataContains(jsonFilter);
            return results != null ? results : List.of();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid metadata filter");
        }
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
            params.put("details", Map.of(
                    "startDate", startDate.toString(),
                    "endDate", endDate.toString()));

            // Create common event via Factory
            MongoEvent event = EventFactory.createEvent(EventType.PROPOSAL, params);

            if (event instanceof ProposalEvent proposalEvent) {
                proposalEventRepository.save(proposalEvent);
            } else {
                System.err.println("WARN: Expected ProposalEvent but got " + event.getClass().getSimpleName());
            }
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
    public void recordInteraction(@NonNull Long proposalId) {
        Proposal proposal = getProposalById(proposalId);

        // a) Validate Status — only SUBMITTED proposals may record interactions
        ProposalStatus status = proposal.getStatus();
        if (status == ProposalStatus.ACCEPTED
                || status == ProposalStatus.REJECTED
                || status == ProposalStatus.SHORTLISTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot record interaction for proposal in status " + status);
        }
        if (status != ProposalStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Proposal must be in SUBMITTED status to record interaction");
        }

        // b) Idempotency check (Neo4j) — do NOT return early; still ensure edge
        // properties exist
        boolean alreadyRecorded;
        try {
            alreadyRecorded = neo4jInteractionRepository.isInteractionRecorded(
                    proposal.getFreelancerId(), proposal.getJobId(), proposalId);
        } catch (Exception e) {
            // Neo4j soft dependency — degrade gracefully
            return;
        }

        // c) Fetch missing data via Feign clients
        String freelancerName = "Unknown Freelancer";
        String jobTitle = "Unknown Job";
        String jobCategory = "OTHER";

        try {
            var user = userServiceClient.getUser(proposal.getFreelancerId());
            if (user != null) {
                freelancerName = user.getName();
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch freelancer details via Feign for proposalId={}", proposalId, e);
        }

        try {
            var job = jobServiceClient.getJob(proposal.getJobId());
            if (job != null) {
                jobTitle = job.getTitle();
                jobCategory = job.getCategory();
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch job details via Feign for proposalId={}", proposalId, e);
        }

        // d) Mutate Neo4j Graph
        try {
            neo4jInteractionRepository.recordInteraction(
                    proposal.getFreelancerId(), freelancerName,
                    proposal.getJobId(), jobTitle, jobCategory,
                    proposalId);
        } catch (Exception e) {
            // Neo4j soft dependency — degrade gracefully
            return;
        }

        // e) Log Mongo event only when this proposal id is newly recorded (idempotent retries skip)
        if (!alreadyRecorded) {
            eventSubject.notifyObservers("INTERACTION_RECORDED", proposal);
        }

        // f) Invalidate S3-F12 Cache
        cacheEvictionService.evictS3F12Recommendations();
    }

    // ── S3-F12: Get Recommended Jobs for Freelancer ──────────────────────

    @Cacheable(value = "proposal-service::S3-F12", key = "#freelancerId + '-' + #limit")
    public List<JobRecommendationDTO> getRecommendedJobsForFreelancer(@NonNull Long freelancerId, int limit) {
        // Verify freelancer exists via UserService Feign client (avoid direct DB access)
        try {
            userServiceClient.getUser(freelancerId);
        } catch (FeignException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Freelancer not found");
        } catch (Exception e) {
            logger.warn("UserService unreachable while verifying freelancerId={}", freelancerId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }

        // Traverse recommendation graph in Neo4j (DP-7 Adapter maps Record -> DTO)
        List<JobRecommendationDTO> rawRecommendations;
        try {
            String cypher = "MATCH (f:Freelancer {userId: $freelancerId})-[:PROPOSED_TO]->(common:Job)<-[:PROPOSED_TO]-(similar:Freelancer) "
                    +
                    "WHERE similar.userId <> f.userId " +
                    "MATCH (similar)-[:PROPOSED_TO]->(rec:Job) " +
                    "WHERE NOT (f)-[:PROPOSED_TO]->(rec) " +
                    "RETURN rec.jobId AS jobId, count(DISTINCT similar) AS score " +
                    "ORDER BY score DESC " +
                    "LIMIT $limit";

            java.util.Collection<JobRecommendationDTO> raw = neo4jClient.query(cypher)
                    .bind(freelancerId).to("freelancerId")
                    .bind(limit).to("limit")
                    .fetchAs(JobRecommendationDTO.class)
                    .mappedBy((typeSystem, record) -> neo4jRecordAdapter.adapt(record))
                    .all();

            rawRecommendations = (raw == null) ? List.of() : List.copyOf(raw);
        } catch (Exception e) {
            // Neo4j soft dependency — degrade gracefully
            return List.of();
        }

        if (rawRecommendations == null || rawRecommendations.isEmpty()) {
            return List.of();
        }

        // Enrich with job details via Feign client
        return rawRecommendations.stream().map(rec -> {
            String jobTitle = "Unknown Job";
            String jobCategory = "OTHER";
            
            try {
                var job = jobServiceClient.getJob(rec.getJobId());
                if (job != null) {
                    jobTitle = job.getTitle();
                    jobCategory = job.getCategory();
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch job details via Feign for jobId={}", rec.getJobId(), e);
            }
            
            return new JobRecommendationDTO(rec.getJobId(), jobTitle, jobCategory, rec.getScore());
        }).toList();
    }

    // ── M3: Feign Client Try-Catch Wrappers ─────────────────────────────────

    public UserDTO getUser(Long userId) {
        try {
            return userServiceClient.getUser(userId);
        } catch (Exception e) {
            System.err.println("WARN: Failed to fetch user " + userId + " via Feign: " + e.getMessage());
            return null;
        }
    }

    public JobDTO getJob(Long jobId) {
        try {
            return jobServiceClient.getJob(jobId);
        } catch (Exception e) {
            System.err.println("WARN: Failed to fetch job " + jobId + " via Feign: " + e.getMessage());
            return null;
        }
    }

    public ContractDTO getContract(Long contractId) {
        try {
            return contractServiceClient.getContract(contractId);
        } catch (Exception e) {
            System.err.println("WARN: Failed to fetch contract " + contractId + " via Feign: " + e.getMessage());
            return null;
        }
    }

    public ContractDTO getActiveContractForProposal(Long proposalId) {
        try {
            return contractServiceClient.getActiveContractForProposal(proposalId);
        } catch (Exception e) {
            System.err.println("WARN: Failed to fetch active contract for proposal " + proposalId + " via Feign: " + e.getMessage());
            return null;
        }
    }

// ── M3: Job Proposal Summary Endpoint Logic ─────────────────────────────

    public com.team26.freelance.contracts.dto.JobProposalSummaryDTO getJobProposalSummary(Long jobId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Object[]> results = proposalRepository.getJobProposalSummaryAggregations(jobId, start, end);

        // Return zeros using the contracts DTO constructor
        if (results == null || results.isEmpty() || results.get(0)[0] == null || ((Number) results.get(0)[0]).longValue() == 0) {
            return new com.team26.freelance.contracts.dto.JobProposalSummaryDTO(
                    0L, 0L, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
        }

        Object[] row = results.get(0);
        long totalProposals = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long acceptedProposals = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        double averageBid = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
        double lowestBid = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
        double highestBid = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;

        // Return populated DTO, converting doubles to BigDecimals
        return new com.team26.freelance.contracts.dto.JobProposalSummaryDTO(
                totalProposals,
                acceptedProposals,
                java.math.BigDecimal.valueOf(averageBid),
                java.math.BigDecimal.valueOf(lowestBid),
                java.math.BigDecimal.valueOf(highestBid)
        );
    }
    /**
     * Saga abandonment reaper: detects proposals stuck in PAYMENT_PENDING beyond
     * saga.payout.abandon-after (default PT72H, configurable in application.yml).
     * Publishes payment.failed compensation event with reason="payout_abandoned".
     * 
     * Runs every 15 minutes (@Scheduled).
     */
    @Scheduled(fixedDelayString = "PT15M")
    @Transactional
    public void reapAbandonedSagaPayouts() {
        try {
            logger.info("Saga abandonment reaper: checking for abandoned payouts...");
            
            String abandonAfterStr = System.getProperty("saga.payout.abandon-after", "PT72H");
            Duration abandonAfter = Duration.parse(abandonAfterStr);
            LocalDateTime abandonThreshold = LocalDateTime.now().minus(abandonAfter);
            
            // Find all proposals in PAYMENT_PENDING status submitted before abandonThreshold
            List<Proposal> abandonedProposals = proposalRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProposalStatus.PAYMENT_PENDING)
                .filter(p -> p.getSubmittedAt() != null && p.getSubmittedAt().isBefore(abandonThreshold))
                .toList();
            
            for (Proposal proposal : abandonedProposals) {
                MDC.put("proposalId", proposal.getId().toString());
                logger.warn("Proposal {} stuck in PAYMENT_PENDING since {} (threshold: {}). Publishing compensation event.",
                    proposal.getId(), proposal.getSubmittedAt(), abandonThreshold);
                
                try {
                    // Publish payment.failed compensation event
                    proposalEventPublisher.publishProposalCancelled(
                        proposal.getId(),
                        proposal.getJobId(),
                        proposal.getFreelancerId(),
                        "payout_abandoned"
                    );
                } catch (Exception e) {
                    logger.error("Failed to publish compensation event for abandoned proposal {}", proposal.getId(), e);
                }
                
                MDC.remove("proposalId");
            }
            
            if (abandonedProposals.isEmpty()) {
                logger.debug("No abandoned proposals detected");
            } else {
                logger.info("Saga abandonment reaper: {} proposals processed", abandonedProposals.size());
            }
        } catch (Exception e) {
            logger.error("Error in saga abandonment reaper", e);
        }
    }

}
