package com.team26.freelance.job.controller;

import com.team26.freelance.job.dto.JobAttachmentAlertDTO;
import com.team26.freelance.job.dto.TopBudgetJobDTO;
import com.team26.freelance.job.dto.JobProposalSummaryDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobStatus;
import com.team26.freelance.job.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @CacheEvict(value = {"job", "job-attachments", "job-proposal-summary", "top-budget-jobs"}, allEntries = true)
    public Job createJob(@RequestBody Job job) {
        return jobService.createJob(job);
    }

    @GetMapping
    public List<Job> getAllJobs() {
        return jobService.getAllJobs();
    }

    @GetMapping("/{id}")
    @Cacheable(value = "job", key = "'job:' + #id")
    public Job getJobById(@PathVariable Long id) {
        return jobService.getJobById(id);
    }

    @PutMapping("/{id}")
    @CacheEvict(value = {"job", "job-attachments", "job-proposal-summary", "top-budget-jobs"}, allEntries = true)
    public Job updateJob(
            @PathVariable Long id,
            @RequestBody Job job
    ) {
        return jobService.updateJob(id, job);
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = {"job", "job-attachments", "job-proposal-summary", "top-budget-jobs"}, allEntries = true)
    public void deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
    }

    @PutMapping("/{id}/close")
    @CacheEvict(value = {"job", "job-attachments", "job-proposal-summary", "top-budget-jobs"}, allEntries = true)
    public ResponseEntity<Void> closeJob(@PathVariable Long id, @RequestBody Map<String, String> body) {
        jobService.closeJob(id, body.get("status"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/requirements/search")
    public ResponseEntity<List<Job>> searchByRequirement(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) JobStatus status) {
        return ResponseEntity.ok(jobService.filterByRequirement(key, value, status));
    }

    @PostMapping("/{id}/rate")
    @ResponseStatus(HttpStatus.OK)
    @CacheEvict(value = {"job", "job-proposal-summary", "top-budget-jobs"}, allEntries = true)
    public Job rateJobClient(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Long contractId = Long.valueOf(body.get("contractId").toString());
        int rating = Integer.parseInt(body.get("rating").toString());
        return jobService.rateJobClient(id, contractId, rating);
    }

    @PutMapping("/{id}/requirements")
    @CacheEvict(value = {"job", "job-proposal-summary"}, key = "'job:' + #id")
    public Job updateRequirements(
            @PathVariable Long id,
            @RequestBody Map<String, Object> requirements) {
        return jobService.updateRequirements(id, requirements);
    }

    @GetMapping("/search")
    public List<Job> searchJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minBudget,
            @RequestParam(required = false) Double maxBudget) {
        return jobService.searchJobs(status, minBudget, maxBudget);
    }

    @GetMapping("/reports/top-budget")
    @Cacheable(value = "top-budget-jobs", key = "'top-budget:' + #limit")
    public ResponseEntity<List<TopBudgetJobDTO>> getTopBudgetJobs(@RequestParam(defaultValue = "10") int limit) {
        if (limit <= 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(jobService.getTopBudgetJobs(limit));
    }

    @GetMapping("/attachments/expired")
    public ResponseEntity<List<JobAttachmentAlertDTO>> getExpiredAttachments() {
        return ResponseEntity.ok(jobService.getJobsWithExpiredAttachments());
    }

    @GetMapping("/{id}/proposal-summary")
    @Cacheable(value = "job-proposal-summary", key = "'job-proposal-summary:' + #id + ':' + (#startDate != null ? #startDate : 'null') + ':' + (#endDate != null ? #endDate : 'null')")
    public JobProposalSummaryDTO getProposalSummary(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return jobService.getProposalSummary(id, startDate, endDate);
    }
}