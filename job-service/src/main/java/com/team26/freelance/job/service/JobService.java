package com.team26.freelance.job.service;

import com.team26.freelance.job.dto.JobAttachmentAlertDTO;
import com.team26.freelance.job.dto.JobDashboardDTO;
import com.team26.freelance.job.dto.TopBudgetJobDTO;
import com.team26.freelance.job.dto.JobProposalSummaryDTO;
import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.model.JobStatus;
import com.team26.freelance.job.model.mongo.JobEvent;
import com.team26.freelance.job.repository.JobRepository;
import com.team26.freelance.job.repository.mongo.JobEventRepository;
import org.springframework.http.HttpStatus;

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


    public JobService(JobRepository jobRepository, JobSearchService jobSearchService, JobEventRepository jobEventRepository) {
        this.jobRepository = jobRepository;
        this.jobSearchService = jobSearchService;
        this.jobEventRepository = jobEventRepository;
    }

    public Job createJob(Job job) {
        Job saved = jobRepository.save(job);
        jobSearchService.indexJob(saved.getId(), "auto_crud_create");
        return saved;
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

    public Job updateJob(Long jobId, Job updatedJob) {
        Job existingJob = getJobById(jobId);

        existingJob.setTitle(updatedJob.getTitle());
        existingJob.setDescription(updatedJob.getDescription());
        existingJob.setCategory(updatedJob.getCategory());
        existingJob.setStatus(updatedJob.getStatus());
        existingJob.setBudgetMin(updatedJob.getBudgetMin());
        existingJob.setBudgetMax(updatedJob.getBudgetMax());
        existingJob.setRequirements(updatedJob.getRequirements());

        Job updated = jobRepository.save(existingJob);
        jobSearchService.indexJob(updated.getId(), "auto_crud_update");
        return updated;
    }

    public void deleteJob(Long jobId) {
        Job job = getJobById(jobId);
        jobRepository.delete(job);
        jobSearchService.removeFromIndex(jobId);
    }

    @Transactional
    public void closeJob(Long id, String status) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if ("CLOSED".equalsIgnoreCase(status)) {
            boolean hasActiveContracts = jobRepository.existsActiveContractByJobId(id);
            if (hasActiveContracts) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot close job with active contracts");
            }

            jobRepository.rejectSubmittedProposalsByJobId(id);

            job.setStatus(JobStatus.CLOSED);
            jobRepository.save(job);
        }
    }


    public List<Job> filterByRequirement(String key, String value, JobStatus status) {
        String statusStr = status != null ? status.name() : null;
        return jobRepository.findByRequirementAndStatus(key, value, statusStr);
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5"
            );
        }
    }

    private void validateContractForJob(Long jobId, Long contractId) {
        var rowOpt = jobRepository.findContractJobIdAndStatusById(contractId);
        if (rowOpt == null || rowOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
        }

        Object[] row = (Object[]) rowOpt.get()[0];
        if (row == null || row.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
        }

        Long contractJobId = ((Long) row[0]).longValue();
        String status = (String) row[1];

        if (!jobId.equals(contractJobId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract does not reference this job");
        }

        if (!"COMPLETED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract is not completed");
        }
    }

    @Transactional
    public Job rateJobClient(Long jobId, Long contractId, int rating) {

        // 1. Find job — 404 if not found
        Job job = getJobById(jobId);



        // 2. Validate contract — 404 if not found, 400 if wrong job or not COMPLETED
        validateContractForJob(jobId, contractId);

        // 3. Validate rating range — 400 if invalid
        validateRating(rating);

        // 4. Recalculate running average
        double currentRating = job.getRating();
        int totalRatings = job.getTotalRatings();
        double newRating = (currentRating * totalRatings + rating) / (totalRatings + 1);

        job.setRating(newRating);
        job.setTotalRatings(totalRatings + 1);

        // 5. Save and return
        return jobRepository.save(job);
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
        return jobRepository.save(job);
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
                .map(row -> new TopBudgetJobDTO(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).longValue()
                ))
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

            return new JobAttachmentAlertDTO(
                job.getId(),
                job.getTitle(),
                job.getStatus(),        // JobStatus enum directly
                expiredAttachments,     // List<JobAttachment> directly
                expiredAttachments.size() // int, not long
            );
        })
        .filter(dto -> dto.getExpiredCount() > 0)
        .toList();
}



    public JobProposalSummaryDTO getProposalSummary(Long jobId, LocalDate startDate, LocalDate endDate) {
        getJobById(jobId);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        String start = startDate != null ? startDate.atStartOfDay().toString() : null;
        String end = endDate != null ? endDate.atTime(23, 59, 59).toString() : null;

        List<Object[]> results = jobRepository.getProposalSummary(jobId, start, end);

        if (results == null || results.isEmpty() || results.get(0) == null) {
            Job job = getJobById(jobId);
            return new JobProposalSummaryDTO(
                    jobId,
                    job.getTitle(),
                    0L,
                    0.0,
                    0.0,
                    0.0
            );
        }

        Object[] result = results.get(0);

        return new JobProposalSummaryDTO(
                ((Number) result[0]).longValue(),
                (String) result[1],
                ((Number) result[2]).longValue(),
                ((Number) result[3]).doubleValue(),
                ((Number) result[4]).doubleValue(),
                ((Number) result[5]).doubleValue()
        );
    }

    public JobDashboardDTO getJobDashboard(Long jobId, Long userId) {
        Job job = getJobById(jobId);

        Long totalProposals = jobRepository.countTotalProposalsByJobId(jobId);
        Long acceptedProposals = jobRepository.countAcceptedProposalsByJobId(jobId);
        Double averageBidAmount = jobRepository.getAverageBidAmountByJobId(jobId);
        Long activeAttachments = jobRepository.countActiveAttachmentsByJobId(jobId);

        JobEvent event = new JobEvent(jobId, "DASHBOARD_VIEWED", null);
        jobEventRepository.save(event);

        return new JobDashboardDTO.Builder()
                .jobId(jobId)
                .title(job.getTitle())
                .totalProposals(totalProposals)
                .acceptedProposals(acceptedProposals)
                .averageBidAmount(averageBidAmount)
                .activeAttachments(activeAttachments)
                .rating(job.getRating())
                .build();
    }
}