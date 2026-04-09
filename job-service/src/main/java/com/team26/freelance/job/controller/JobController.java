package com.team26.freelance.job.controller;

import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobStatus;
import com.team26.freelance.job.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public Job createJob(@RequestBody Job job) {
        return jobService.createJob(job);
    }

    @GetMapping
    public List<Job> getAllJobs() {
        return jobService.getAllJobs();
    }

    @GetMapping("/{id}")
    public Job getJobById(@PathVariable Long id) {
        return jobService.getJobById(id);
    }

    @PutMapping("/{id}")
    public Job updateJob(
            @PathVariable Long id,
            @RequestBody Job job
    ) {
        return jobService.updateJob(id, job);
    }

    @DeleteMapping("/{id}")
    public void deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
    }

    @GetMapping("/requirements/search")
    public ResponseEntity<List<Job>> searchByRequirement(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) JobStatus status) {
        return ResponseEntity.ok(jobService.filterByRequirement(key, value, status));
    }
}