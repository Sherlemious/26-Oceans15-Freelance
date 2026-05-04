package com.team26.freelance.job.service;

import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.repository.JobAttachmentRepository;
import com.team26.freelance.job.repository.JobRepository;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class JobAttachmentService {

    private final JobAttachmentRepository jobAttachmentRepository;
    private final JobRepository jobRepository;
    private final JobSearchService jobSearchService;
    

    public JobAttachmentService(JobAttachmentRepository jobAttachmentRepository,
                                JobRepository jobRepository,
                                JobSearchService jobSearchService) {
        this.jobAttachmentRepository = jobAttachmentRepository;
        this.jobRepository = jobRepository;
        this.jobSearchService = jobSearchService;
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

        JobAttachment saved = jobAttachmentRepository.save(attachment);
        jobSearchService.notifyObservers("JOB_ATTACHMENT_CREATED", Map.of(
                "jobId", jobId,
                "attachmentId", saved.getId(),
                "source", "auto_crud_create"
        ));
        return saved;
    }

    public List<JobAttachment> getAllAttachmentsForJob(Long jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }

        return jobAttachmentRepository.findByJobId(jobId);
    }

    public JobAttachment getAttachmentById(Long attachmentId, Long jobId) {
        JobAttachment attachment = jobAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "JobAttachment not found"
                ));

        if (!attachment.getJob().getId().equals(jobId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment does not belong to the specified job");
        }

        return attachment;
    }

    public JobAttachment updateAttachment(Long attachmentId, JobAttachment updatedAttachment, Long jobId) {
        JobAttachment existingAttachment = getAttachmentById(attachmentId, jobId);

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

        JobAttachment updated = jobAttachmentRepository.save(existingAttachment);
        jobSearchService.notifyObservers("JOB_ATTACHMENT_UPDATED", Map.of(
                "jobId", jobId,
                "attachmentId", attachmentId,
                "source", "auto_crud_update"
        ));
        return updated;
    }

    public void deleteAttachment(Long attachmentId, Long jobId) {
        JobAttachment attachment = getAttachmentById(attachmentId, jobId);
        jobAttachmentRepository.delete(attachment);
        jobSearchService.notifyObservers("JOB_ATTACHMENT_DELETED", Map.of(
                "jobId", jobId,
                "attachmentId", attachmentId,
                "source", "auto_crud_delete"
        ));
    }

    public void verifyUserRole(Long userId){
        Optional<String> roleOpt = jobAttachmentRepository.findUserRoleById(userId);
        if (roleOpt == null || !roleOpt.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        String role = roleOpt.get();
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have permission to verify attachments");
        }
    }
    @Transactional
    public Job verifyAttachment(Long jobId, Long attachmentId, Long verifiedBy) {
        
        verifyUserRole(verifiedBy);


        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Job not found"
                ));

        JobAttachment attachment = getAttachmentById(attachmentId, jobId);
                

        if (!attachment.getJob().getId().equals(jobId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment does not belong to the specified job");
        }

        // check the attachment is not expired
        if (attachment.getExpiryDate() != null && attachment.getExpiryDate().isBefore(java.time.LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment has expired");
        }

        // check if already verified
        if (attachment.getVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment is already verified");
        }

        

        // add verifiedAt to metadata
        attachment.getMetadata().put("verifiedAt", java.time.LocalDateTime.now().toString());
        attachment.getMetadata().put("verifiedBy", verifiedBy);


        attachment.setVerified(true);
        
        jobAttachmentRepository.save(attachment);

        jobSearchService.notifyObservers("JOB_ATTACHMENT_VERIFIED", Map.of(
                "jobId", jobId,
                "attachmentId", attachmentId,
                "source", "attachment_verification"
        ));

        return attachment.getJob();
    }
}