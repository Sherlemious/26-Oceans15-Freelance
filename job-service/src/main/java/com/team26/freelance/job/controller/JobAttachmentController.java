package com.team26.freelance.job.controller;

import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.service.JobAttachmentService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs/{jobId}/attachments")
public class JobAttachmentController {

    private final JobAttachmentService jobAttachmentService;

    public JobAttachmentController(JobAttachmentService jobAttachmentService) {
        this.jobAttachmentService = jobAttachmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobAttachment createAttachment(
            @PathVariable Long jobId,
            @RequestBody JobAttachment attachment
    ) {
        return jobAttachmentService.createAttachment(jobId, attachment);
    }

    @GetMapping
    public List<JobAttachment> getAllAttachmentsForJob(@PathVariable Long jobId) {
        return jobAttachmentService.getAllAttachmentsForJob(jobId);
    }

    @GetMapping("/{attachmentId}")
    @Cacheable(value = "job-attachments", key = "'job-service::job-attachments::' + #jobId")
    public JobAttachment getAttachmentById(
            @PathVariable Long attachmentId,
            @PathVariable Long jobId
    ) {
        return jobAttachmentService.getAttachmentById(attachmentId, jobId);
    }

    @PutMapping("/{attachmentId}")
    public JobAttachment updateAttachment(
            @PathVariable Long attachmentId,
            @RequestBody JobAttachment attachment,
            @PathVariable Long jobId
    ) {
        return jobAttachmentService.updateAttachment(attachmentId, attachment, jobId);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(
            @PathVariable Long attachmentId,
            @PathVariable Long jobId
    ) {
        jobAttachmentService.deleteAttachment(attachmentId, jobId);
    }


    // Verify Job Attachment
    @PutMapping("/{attachmentId}/verify")
    public Job verifyAttachment(
            @PathVariable Long jobId,
            @PathVariable Long attachmentId,
            @RequestBody Map<String, Object> body
    ) {
        Long verifiedBy = Long.valueOf(body.get("verifiedBy").toString());
        System.out.println("Verifying attachment " + attachmentId + " for job " + jobId + " by user " + verifiedBy);
        return jobAttachmentService.verifyAttachment(jobId, attachmentId, verifiedBy);
    }
}