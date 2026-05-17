package com.team26.freelance.job.controller;

import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.job.dto.*;
import com.team26.freelance.job.service.CacheEvictionService;
import com.team26.freelance.job.service.JobSearchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobStatus;
import com.team26.freelance.job.service.JobService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final CacheEvictionService cacheEvictionService;
    private final JobSearchService jobSearchService;
    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    public JobController(JobService jobService, CacheEvictionService cacheEvictionService, JobSearchService jobSearchService) {
        this.jobService = jobService;
        this.cacheEvictionService = cacheEvictionService;
        this.jobSearchService = jobSearchService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobSearchResultDTO createJob(@Valid @RequestBody JobRequestDTO job) {
        log.info("Received POST /api/jobs");
        JobSearchResultDTO result = jobService.createJob(job);
        log.info("Returning 201 for POST /api/jobs");
        return result;
    }

    @GetMapping
    public List<Job> getAllJobs() {
        log.info("Received GET /api/jobs");
        List<Job> result = jobService.getAllJobs();
        log.info("Returning 200 for GET /api/jobs");
        return result;
    }

    @GetMapping("/{id}")
    @Cacheable(value = "job", key = "'job-service::job::' + #id")
    public JobDTO getJobById(@PathVariable Long id) {
        log.info("Received GET /api/jobs/{}", id);
        JobDTO result = jobService.getJobAsDTO(id);
        log.info("Returning 200 for GET /api/jobs/{}", id);
        return result;
    }


    @PutMapping("/{id}")
    @CacheEvict(value = "job", key = "'job-service::job::' + #id")
    public Job updateJob(@PathVariable Long id, @RequestBody Job job) {
        log.info("Received PUT /api/jobs/{}", id);
        Job updated = jobService.updateJob(id, job);
        cacheEvictionService.evictAllJobFeatureCaches();
        log.info("Returning 200 for PUT /api/jobs/{}", id);
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(value = "job", key = "'job-service::job::' + #id")
    public void deleteJob(@PathVariable Long id) {
        log.info("Received DELETE /api/jobs/{}", id);
        jobService.deleteJob(id);
        cacheEvictionService.evictAllJobFeatureCaches();
        log.info("Returning 204 for DELETE /api/jobs/{}", id);
    }

    @PutMapping("/{id}/close")
    @CacheEvict(value = "job", key = "'job-service::job::' + #id")
    public ResponseEntity<Void> closeJob(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        log.info("Received PUT /api/jobs/{}/close", id);
        jobService.closeJob(id, body.get("status"));
        cacheEvictionService.evictAllJobFeatureCaches();
        log.info("Returning 200 for PUT /api/jobs/{}/close", id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/requirements/search")
    @Cacheable(value = "job-requirements-search", key = "'job-service::S2-F5::' + #key + ':' + #value + ':' + (#status != null ? #status : 'null')")
    public ResponseEntity<List<Job>> searchByRequirement(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) JobStatus status) {

        if (key == null || key.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key parameter cannot be blank");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value parameter cannot be blank");
        }
        return ResponseEntity.ok(jobService.filterByRequirement(key, value, status));
    }

    @PostMapping("/{id}/rate")
    @ResponseStatus(HttpStatus.OK)
    @CacheEvict(value = "job", key = "'job-service::job::' + #id")
    public Job rateJobClient(@PathVariable Long id,
                             @RequestBody Map<String, Object> body) {
        log.info("Received POST /api/jobs/{}/rate", id);
        Long contractId = Long.valueOf(body.get("contractId").toString());
        int rating = Integer.parseInt(body.get("rating").toString());
        Job result = jobService.rateJobClient(id, contractId, rating);
        cacheEvictionService.evictAllJobFeatureCaches();
        log.info("Returning 200 for POST /api/jobs/{}/rate", id);
        return result;
    }

    @PutMapping("/{id}/requirements")
    @CacheEvict(value = "job", key = "'job-service::job::' + #id")
    public Job updateRequirements(@PathVariable Long id,
                                  @RequestBody Map<String, Object> requirements) {
        Job updated = jobService.updateRequirements(id, requirements);
        cacheEvictionService.evictByPattern("job-service::S2-F3::*");
        return updated;
    }

    @GetMapping("/search")
    @Cacheable(value = "job-search", key = "'job-service::S2-F1::' + #status + ':' + #minBudget + ':' + #maxBudget")
    public List<Job> searchJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minBudget,
            @RequestParam(required = false) Double maxBudget) {
        return jobService.searchJobs(status, minBudget, maxBudget);
    }

    @GetMapping("/reports/top-budget")
    @Cacheable(value = "top-budget-jobs", key = "'job-service::S2-F6::' + #limit")
    public List<TopBudgetJobDTO> getTopBudgetJobs(
            @RequestParam(defaultValue = "10") int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be > 0");
        }
        return jobService.getTopBudgetJobs(limit);
    }

    @GetMapping("/attachments/expired")
    @Cacheable(value = "job-expired-attachments", key = "'job-service::S2-F9::all'")
    public List<JobAttachmentAlertDTO> getExpiredAttachments() {
        return jobService.getJobsWithExpiredAttachments();
    }

    @GetMapping("/{id}/proposal-summary")
    @Cacheable(value = "job-proposal-summary", key = "'job-service::S2-F3::' + #id + ':' + (#startDate != null ? #startDate : 'null') + ':' + (#endDate != null ? #endDate : 'null')")
    public JobProposalSummaryDTO getProposalSummary(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Received GET /api/jobs/{}/proposal-summary", id);
        JobProposalSummaryDTO result = jobService.getProposalSummary(id, startDate, endDate);
        log.info("Returning 200 for GET /api/jobs/{}/proposal-summary", id);
        return result;
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/index")
    public ResponseEntity<Void> indexJob(@PathVariable Long id) {
        boolean indexed = jobSearchService.indexJob(id, "explicit");
        if (!indexed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/search/full-text")
    @Cacheable(
        value  = "fullTextJobSearch",
        key    = "'job-service::S2-F10::' + #query + ':' + #category + ':' + #status + ':' + #minBudget + ':' + #maxBudget",
        unless = "#result.body == null || #result.body.isEmpty()"
    )
    public ResponseEntity<List<JobSearchResultDTO>> fullTextSearch(
            @RequestParam String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minBudget,
            @RequestParam(required = false) Double maxBudget
        ) {
        return ResponseEntity.ok(jobSearchService.fullTextSearchResults(query, category, status, minBudget, maxBudget));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{id}/dashboard")
    @Cacheable(value = "job-dashboard", key = "'job-service::S2-F12::' + #id")
    public ResponseEntity<JobDashboardDTO> getJobDashboard(@PathVariable Long id) {
        log.info("Received GET /api/jobs/{}/dashboard", id);
        jobService.logDashboardViewed(id);
        JobDashboardDTO result = jobService.getJobDashboard(id);
        log.info("Returning 200 for GET /api/jobs/{}/dashboard", id);
        return ResponseEntity.ok(result);
    }
}