package com.team26.freelance.job.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.team26.freelance.job.dto.JobAttachmentAlertDTO;
import com.team26.freelance.job.dto.TopBudgetJobDTO;
import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.model.JobStatus;
import com.team26.freelance.job.repository.JobRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final RestTemplate restTemplate;

    @Value("${contract.service.url}")
    private String contractServiceUrl;

    public JobService(JobRepository jobRepository, RestTemplate restTemplate) {
        this.jobRepository = jobRepository;
        this.restTemplate = restTemplate;
    }

    public Job createJob(Job job) {
        return jobRepository.save(job);
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

        return jobRepository.save(existingJob);
    }

    public void deleteJob(Long jobId) {
        Job job = getJobById(jobId);
        jobRepository.delete(job);
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
        JsonNode contract;
        try {
            contract = restTemplate.getForObject(
                    contractServiceUrl + "/api/contracts/" + contractId,
                    JsonNode.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
        }

        if (contract == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
        }

        // verify contract references this job
        Long contractJobId = contract.get("jobId").asLong();
        if (!jobId.equals(contractJobId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Contract does not reference this job"
            );
        }

        // verify contract is COMPLETED
        String status = contract.get("status").asText();
        if (!"COMPLETED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Contract is not completed"
            );
        }
    }

    @Transactional
    public Job rateJobClient(Long jobId, Long contractId, int rating) {

        // 1. Find job — 404 if not found
        Job job = getJobById(jobId);

        // 2. Validate rating range — 400 if invalid
        validateRating(rating);

        // 3. Validate contract — 404 if not found, 400 if wrong job or not COMPLETED
        validateContractForJob(jobId, contractId);

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


    
}