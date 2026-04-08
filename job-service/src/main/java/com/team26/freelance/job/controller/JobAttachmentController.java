package com.team26.freelance.job.controller;

import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.service.JobAttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public JobAttachment getAttachmentById(
            @PathVariable Long attachmentId
    ) {
        return jobAttachmentService.getAttachmentById(attachmentId);
    }

    @PutMapping("/{attachmentId}")
    public JobAttachment updateAttachment(
            @PathVariable Long attachmentId,
            @RequestBody JobAttachment attachment
    ) {
        return jobAttachmentService.updateAttachment(attachmentId, attachment);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(
            @PathVariable Long attachmentId
    ) {
        jobAttachmentService.deleteAttachment(attachmentId);
    }
}