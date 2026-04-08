package com.team26.freelance.job.service;

import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.repository.JobAttachmentRepository;
import com.team26.freelance.job.repository.JobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class JobAttachmentService {

    private final JobAttachmentRepository jobAttachmentRepository;
    private final JobRepository jobRepository;

    public JobAttachmentService(JobAttachmentRepository jobAttachmentRepository,
                                JobRepository jobRepository) {
        this.jobAttachmentRepository = jobAttachmentRepository;
        this.jobRepository = jobRepository;
    }

    public JobAttachment createAttachment(Long jobId, JobAttachment attachment) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Job not found"
                ));

        attachment.setJob(job);

        if (attachment.getVerified() == null) {
            attachment.setVerified(false);
        }

        return jobAttachmentRepository.save(attachment);
    }

    public List<JobAttachment> getAllAttachmentsForJob(Long jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }

        return jobAttachmentRepository.findByJobId(jobId);
    }

    public JobAttachment getAttachmentById(Long attachmentId) {
        return jobAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "JobAttachment not found"
                ));
    }

    public JobAttachment updateAttachment(Long attachmentId, JobAttachment updatedAttachment) {
        JobAttachment existingAttachment = getAttachmentById(attachmentId);

        if (updatedAttachment.getType() != null) {
            existingAttachment.setType(updatedAttachment.getType());
        }
        if (updatedAttachment.getFileUrl() != null) {
            existingAttachment.setFileUrl(updatedAttachment.getFileUrl());
        }
        if (updatedAttachment.getExpiryDate() != null) {
            existingAttachment.setExpiryDate(updatedAttachment.getExpiryDate());
        }
        if (updatedAttachment.getVerified() != null) {
            existingAttachment.setVerified(updatedAttachment.getVerified());
        }
        if (updatedAttachment.getMetadata() != null && !updatedAttachment.getMetadata().isEmpty()) {
            existingAttachment.getMetadata().putAll(updatedAttachment.getMetadata());
        }

        return jobAttachmentRepository.save(existingAttachment);
    }

    public void deleteAttachment(Long attachmentId) {
        JobAttachment attachment = getAttachmentById(attachmentId);
        jobAttachmentRepository.delete(attachment);
    }
}