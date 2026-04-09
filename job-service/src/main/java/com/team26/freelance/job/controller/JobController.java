package com.team26.freelance.job.controller;
import org.springframework.http.HttpStatus;
import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.service.JobService;
import org.springframework.web.bind.annotation.*;

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


    // Feature 7 : Rate Job Client after Contract (Transactional)
    @PostMapping("/{id}/rate")
    @ResponseStatus(HttpStatus.OK)
    public Job rateJobClient(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {        
        Long contractId = Long.valueOf(body.get("contractId").toString());
        int rating = Integer.parseInt(body.get("rating").toString());
        return jobService.rateJobClient(id,contractId, rating);
    }
}