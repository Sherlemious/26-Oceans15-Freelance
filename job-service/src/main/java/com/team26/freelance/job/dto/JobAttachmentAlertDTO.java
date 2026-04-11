package com.team26.freelance.job.dto;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.model.JobStatus;

import java.util.List;

public class JobAttachmentAlertDTO {

    private Long jobId;
    private String jobTitle;
    private JobStatus jobStatus;
    private List<JobAttachment> expiredAttachments;
    private int expiredCount;

    public JobAttachmentAlertDTO(Long jobId, String jobTitle, JobStatus jobStatus, List<JobAttachment> expiredAttachments, int expiredCount) {
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.jobStatus = jobStatus;
        this.expiredAttachments = expiredAttachments;
        this.expiredCount = expiredCount;
    }

    public Long getJobId() { return jobId; }
    public String getJobTitle() { return jobTitle; }
    public JobStatus getJobStatus() { return jobStatus; }
    public List<JobAttachment> getExpiredAttachments() { return expiredAttachments; }
    public int getExpiredCount() { return expiredCount; }

}
