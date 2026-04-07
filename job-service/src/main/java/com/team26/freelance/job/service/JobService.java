package com.team26.freelance.job.service;

import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.repository.JobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
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

}