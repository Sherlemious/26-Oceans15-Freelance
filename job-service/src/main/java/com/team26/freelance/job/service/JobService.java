package com.team26.freelance.job.service;

import com.team26.freelance.job.dto.*;
import com.team26.freelance.contracts.events.JobClosedEvent;
import com.team26.freelance.contracts.events.JobRatedEvent;
import com.team26.freelance.contracts.events.JobStatusChangedEvent;
import com.team26.freelance.contracts.events.ProposalAcceptedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.ProposalWithdrawnEvent;
import com.team26.freelance.job.feign.ContractDTO;
import com.team26.freelance.job.feign.ContractServiceClient;
import com.team26.freelance.job.feign.ProposalSummaryResponse;
import com.team26.freelance.job.feign.ProposalServiceClient;
import com.team26.freelance.job.messaging.publishers.JobSagaPublisher;
import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.model.JobStatus;
import com.team26.freelance.job.repository.JobRepository;
import com.team26.freelance.job.repository.mongo.JobEventRepository;
import feign.FeignException;
import org.springframework.http.HttpStatus;

import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobSearchService jobSearchService;
    private final JobEventRepository jobEventRepository;
    private final ContractServiceClient contractServiceClient;
    private final ProposalServiceClient proposalServiceClient;
    private final JobSagaPublisher jobSagaPublisher;

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    public JobService(JobRepository jobRepository,
                      JobSearchService jobSearchService,
                      JobEventRepository jobEventRepository,
                      ContractServiceClient contractServiceClient,
                      ProposalServiceClient proposalServiceClient,
                      JobSagaPublisher jobSagaPublisher) {
        this.jobRepository = jobRepository;
        this.jobSearchService = jobSearchService;
        this.jobEventRepository = jobEventRepository;
        this.contractServiceClient = contractServiceClient;
        this.proposalServiceClient = proposalServiceClient;
        this.jobSagaPublisher = jobSagaPublisher;
    }

    public JobSearchResultDTO createJob(JobRequestDTO request) {
        Long clientId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getCredentials().toString()
        );

        Job job = new Job();
        job.setClientId(clientId);
        job.setStatus(JobStatus.OPEN);
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setCategory(request.getCategory());
        job.setBudgetMin(request.getBudgetMin());
        job.setBudgetMax(request.getBudgetMax());

        Job saved = jobRepository.save(job);

        jobSearchService.indexJob(saved.getId(), "auto_crud_create");
        jobSearchService.notifyObservers("JOB_CREATED", Map.of(
                "jobId", saved.getId(),
                "source", "auto_crud_create"
        ));

        return JobSearchResultDTO.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .category(saved.getCategory() != null ? saved.getCategory().name() : null)
                .budgetMin(saved.getBudgetMin())
                .budgetMax(saved.getBudgetMax())
                .build();
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    public Job getJobById(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Job not found"
                ));
    }

    @Transactional
    public Job updateJob(Long jobId, Job updatedJob) {
        Job existingJob = getJobById(jobId);
        JobStatus oldStatus = existingJob.getStatus();

        existingJob.setTitle(updatedJob.getTitle());
        existingJob.setDescription(updatedJob.getDescription());
        existingJob.setCategory(updatedJob.getCategory());
        existingJob.setStatus(updatedJob.getStatus());
        existingJob.setBudgetMin(updatedJob.getBudgetMin());
        existingJob.setBudgetMax(updatedJob.getBudgetMax());
        existingJob.setRequirements(updatedJob.getRequirements());

        Job updated = jobRepository.save(existingJob);
        jobSearchService.indexJob(updated.getId(), "auto_crud_update");
        jobSearchService.notifyObservers("JOB_UPDATED", Map.of(
                "jobId", updated.getId(),
                "source", "auto_crud_update"
        ));
        publishStatusChangedIfNeeded(updated.getId(), oldStatus, updated.getStatus());
        return updated;
    }

    public void deleteJob(Long jobId) {
        Job job = getJobById(jobId);
        jobRepository.delete(job);
        jobSearchService.removeFromIndex(jobId);
    }

    @Transactional
    public void closeJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        JobStatus oldStatus = job.getStatus();

        int activeContracts;
        try {
            activeContracts = contractServiceClient.getActiveContractCountForJob(id);
            log.info("Feign call to contract-service for job {} active contracts: {}", id, activeContracts);
        } catch (FeignException.NotFound e) {
            log.warn("Contract service returned 404 for job {}, assuming 0 active contracts", id);
            activeContracts = 0;
        } catch (FeignException e) {
            log.error("Contract service unavailable for job {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Contract service temporarily unavailable");
        }

        if (activeContracts > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot close job with active contracts");
        }

        try {
            proposalServiceClient.rejectSubmittedProposalsForJob(id);
            log.info("Feign call to proposal-service: rejected submitted proposals for job {}", id);
        } catch (FeignException.NotFound e) {
            log.warn("Proposal service returned 404 for job {}, no proposals to reject", id);
        } catch (FeignException e) {
            log.error("Proposal service unavailable when rejecting proposals for job {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proposal service temporarily unavailable");
        }

        job.setStatus(JobStatus.CLOSED);
        jobRepository.save(job);
        jobSearchService.indexJob(job.getId(), "close_endpoint");
        publishStatusChangedIfNeeded(job.getId(), oldStatus, JobStatus.CLOSED);
        jobSagaPublisher.publishJobClosed(new JobClosedEvent(job.getId(), job.getClientId()));

        jobSearchService.notifyObservers("JOB_CLOSED", Map.of(
                "jobId", id,
                "source", "close_endpoint"
        ));
    }

    @Transactional
    public void closeJob(Long id, String status) {
        if ("CLOSED".equalsIgnoreCase(status)) {
            closeJob(id);
        }
    }

    public List<Job> filterByRequirement(String key, String value, JobStatus status) {
        try {
            String statusStr = status != null ? status.name() : null;
            return jobRepository.findByRequirementAndStatus(key, value, statusStr);
        } catch (Exception e) {
            log.error("Error filtering jobs by requirement: key={}, value={}", key, value, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database error occurred");
        }
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5"
            );
        }
    }

    @Transactional
    public Job rateJobClient(Long jobId, Long contractId, int rating) {
        Job job = getJobById(jobId);
        validateRating(rating);

        ContractDTO contract;
        try {
            contract = contractServiceClient.getContract(contractId);
            log.info("Feign call to contract-service for contract {}: status={}, jobId={}",
                    contractId, contract.status(), contract.jobId());
        } catch (FeignException.NotFound e) {
            log.warn("Contract {} not found in contract-service", contractId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
        } catch (FeignException e) {
            log.error("Contract service unavailable for contract {}: {}", contractId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Contract service temporarily unavailable");
        }

        if (!contract.jobId().equals(jobId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract does not reference this job");
        }

        if (!"COMPLETED".equals(contract.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract is not completed");
        }

        double currentRating = job.getRating();
        int totalRatings = job.getTotalRatings();
        double newRating = (currentRating * totalRatings + rating) / (totalRatings + 1);

        job.setRating(newRating);
        job.setTotalRatings(totalRatings + 1);

        Job updated = jobRepository.save(job);

        jobSearchService.indexJob(jobId, "client_rating");
        jobSearchService.notifyObservers("JOB_RATED", Map.of(
                "jobId", jobId,
                "contractId", contractId,
                "newRating", newRating,
                "totalRatings", updated.getTotalRatings(),
                "source", "client_rating"
        ));

        jobSagaPublisher.publishJobRated(new JobRatedEvent(jobId, contractId, newRating, job.getClientId()));

        return updated;
    }

    @Transactional
    public void handleProposalAccepted(ProposalAcceptedEvent event) {
        transitionJobStatus(event.jobId(), JobStatus.IN_PROGRESS, "proposal.accepted");
    }

    @Transactional
    public void handleProposalCompleted(ProposalCompletedEvent event) {
        transitionJobStatus(event.jobId(), JobStatus.CLOSED, "proposal.completed");
    }

    @Transactional
    public void handleProposalCancelled(ProposalCancelledEvent event) {
        Job job = getJobById(event.jobId());
        if (job.getStatus() != JobStatus.CLOSED) {
            log.info("Ignoring proposal.cancelled for jobId={} because status is {}", event.jobId(), job.getStatus());
            return;
        }

        transitionJobStatus(event.jobId(), JobStatus.IN_PROGRESS, "proposal.cancelled");
    }

    @Transactional
    public void handleProposalWithdrawn(ProposalWithdrawnEvent event) {
        Job job = getJobById(event.jobId());
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            log.info("Ignoring proposal.withdrawn for jobId={} because status is {}", event.jobId(), job.getStatus());
            return;
        }

        try {
            Long proposalCount = proposalServiceClient.getProposalCountForJob(event.jobId());
            if (proposalCount == null || proposalCount > 0) {
                log.info("Keeping jobId={} IN_PROGRESS because proposal count is {}", event.jobId(), proposalCount);
                return;
            }
        } catch (FeignException.NotFound e) {
            log.warn("Proposal count endpoint returned 404 for jobId={}, skipping status reopen", event.jobId());
            return;
        } catch (FeignException e) {
            log.warn("Unable to evaluate withdrawn proposal impact for jobId={}: {}", event.jobId(), e.getMessage());
            return;
        }

        transitionJobStatus(event.jobId(), JobStatus.OPEN, "proposal.withdrawn");
    }

    @Transactional
    public Job updateRequirements(Long jobId, Map<String, Object> newRequirements) {
        Job job = getJobById(jobId);

        Map<String, Object> existingRequirements = job.getRequirements();
        if (existingRequirements == null) {
            existingRequirements = new HashMap<>();
        }

        if (newRequirements != null) {
            existingRequirements.putAll(newRequirements);
        }

        job.setRequirements(existingRequirements);
        Job updatedJob = jobRepository.save(job);

        jobSearchService.indexJob(jobId, "requirements_update");
        jobSearchService.notifyObservers("REQUIREMENTS_UPDATED", Map.of("jobId", jobId));

        return updatedJob;
    }

    public List<Job> searchJobs(String status, Double minBudget, Double maxBudget) {
        if (minBudget != null && maxBudget != null && minBudget > maxBudget) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minBudget cannot be greater than maxBudget");
        }
        return jobRepository.searchJobs(status, minBudget, maxBudget);
    }

    public List<TopBudgetJobDTO> getTopBudgetJobs(int limit) {
        List<Object[]> results = jobRepository.findTopBudgetJobs(limit);
        return results.stream()
                .map(row -> {
                    Long jobId = ((Number) row[0]).longValue();
                    String title = (String) row[1];
                    Double budgetMax = ((Number) row[2]).doubleValue();

                    Long totalProposals = 0L;
                    try {
                        totalProposals = proposalServiceClient.getProposalCountForJob(jobId);
                        log.info("Feign call to proposal-service: proposal count for job {} = {}", jobId, totalProposals);
                    } catch (FeignException.NotFound e) {
                        log.warn("Proposal service returned 404 for job {}, assuming 0 proposals", jobId);
                    } catch (FeignException e) {
                        log.error("Proposal service unavailable for job {}: {}", jobId, e.getMessage());
                    }

                    return TopBudgetJobDTO.builder()
                            .jobId(jobId)
                            .title(title)
                            .budgetMax(budgetMax)
                            .totalProposals(totalProposals)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<JobAttachmentAlertDTO> getJobsWithExpiredAttachments() {
        List<Long> jobIds = jobRepository.findJobIdsWithExpiredAttachments();

        return jobIds.stream()
                .map(jobId -> {
                    Job job = jobRepository.findById(jobId).orElseThrow();

                    List<JobAttachment> expiredAttachments = job.getJobAttachments()
                            .stream()
                            .filter(a -> a.getExpiryDate() != null && a.getExpiryDate().isBefore(LocalDate.now()))
                            .toList();

                    return JobAttachmentAlertDTO.builder()
                            .jobId(job.getId())
                            .jobTitle(job.getTitle())
                            .jobStatus(job.getStatus())
                            .expiredAttachments(expiredAttachments)
                            .expiredCount(expiredAttachments.size())
                            .build();
                })
                .filter(dto -> dto.getExpiredCount() > 0)
                .toList();
    }

    // S2-F3: Get Job Proposal Summary - Refactored to use Feign
    public JobProposalSummaryDTO getProposalSummary(Long jobId, LocalDate startDate, LocalDate endDate) {
        Job job = getJobById(jobId);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;

        ProposalSummaryResponse feignSummary;
        try {
            feignSummary = proposalServiceClient.getJobProposalSummary(jobId, start, end);
            log.info("Feign call to proposal-service for job {} summary: totalProposals={}",
                    jobId, feignSummary.totalProposals());
        } catch (FeignException.NotFound e) {
            log.warn("Proposal service returned 404 for job {}, assuming zero proposals", jobId);
            feignSummary = new ProposalSummaryResponse(0L, 0.0, 0.0, 0.0, 0L);
        } catch (FeignException e) {
            log.error("Proposal service unavailable for job {}: {}", jobId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proposal service temporarily unavailable");
        }

        return JobProposalSummaryDTO.builder()
                .jobId(jobId)
                .title(job.getTitle())
                .totalProposals(feignSummary.totalProposals())
                .averageBidAmount(feignSummary.averageBidAmount())
                .lowestBid(feignSummary.lowestBid())
                .highestBid(feignSummary.highestBid())
                .build();
    }

    public void logDashboardViewed(Long jobId) {
        jobSearchService.notifyObservers("DASHBOARD_VIEWED", Map.of(
                "jobId", jobId,
                "source", "dashboard"
        ));
    }

    // S2-F12: Get Job Dashboard - Refactored to use Feign
    public JobDashboardDTO getJobDashboard(Long jobId) {
        Job job = getJobById(jobId);

        ProposalSummaryResponse feignSummary;
        try {
            feignSummary = proposalServiceClient.getJobProposalSummary(jobId, null, null);
            log.info("Feign call to proposal-service for job {} dashboard", jobId);
        } catch (FeignException.NotFound e) {
            log.warn("Proposal service returned 404 for job {} dashboard, using zeros", jobId);
            feignSummary = new ProposalSummaryResponse(0L, 0.0, 0.0, 0.0, 0L);
        } catch (FeignException e) {
            log.error("Proposal service unavailable for job {} dashboard: {}", jobId, e.getMessage());
            feignSummary = new ProposalSummaryResponse(0L, 0.0, 0.0, 0.0, 0L);
        }

        Long activeAttachments = jobRepository.countActiveAttachmentsByJobId(jobId);

        return JobDashboardDTO.builder()
                .jobId(jobId)
                .title(job.getTitle())
                .totalProposals(feignSummary.totalProposals())
                .acceptedProposals(feignSummary.acceptedProposals() != null ? feignSummary.acceptedProposals() : 0L)
                .averageBidAmount(feignSummary.averageBidAmount())
                .activeAttachments(activeAttachments)
                .rating(job.getRating())
                .build();
    }

    private void transitionJobStatus(Long jobId, JobStatus newStatus, String source) {
        Job job = getJobById(jobId);
        JobStatus oldStatus = job.getStatus();

        if (oldStatus == newStatus) {
            log.info("Skipping status transition for jobId={} because status is already {} from {}", jobId, newStatus, source);
            return;
        }

        job.setStatus(newStatus);
        jobRepository.save(job);
        jobSearchService.indexJob(jobId, source);
        jobSearchService.notifyObservers("JOB_STATUS_CHANGED", Map.of(
                "jobId", jobId,
                "oldStatus", oldStatus.name(),
                "newStatus", newStatus.name(),
                "source", source
        ));
        publishStatusChangedIfNeeded(jobId, oldStatus, newStatus);
        log.info("Updated jobId={} status from {} to {} via {}", jobId, oldStatus, newStatus, source);
    }

    private void publishStatusChangedIfNeeded(Long jobId, JobStatus oldStatus, JobStatus newStatus) {
        if (oldStatus == null || oldStatus == newStatus) {
            return;
        }

        jobSagaPublisher.publishJobStatusChanged(
                new JobStatusChangedEvent(jobId, oldStatus.name(), newStatus.name()));
    }
}
