package com.team26.freelance.job.dto;
import com.team26.freelance.job.model.JobAttachment;
import com.team26.freelance.job.model.JobStatus;

import java.util.List;

public class JobAttachmentAlertDTO {

    private final Long jobId;
    private final String jobTitle;
    private final JobStatus jobStatus;
    private final List<JobAttachment> expiredAttachments;
    private final int expiredCount;

    private JobAttachmentAlertDTO(Builder builder) {
        this.jobId = builder.jobId;
        this.jobTitle = builder.jobTitle;
        this.jobStatus = builder.jobStatus;
        this.expiredAttachments = builder.expiredAttachments;
        this.expiredCount = builder.expiredCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long jobId;
        private String jobTitle;
        private JobStatus jobStatus;
        private List<JobAttachment> expiredAttachments;
        private int expiredCount;

        public Builder jobId(Long jobId) { this.jobId = jobId; return this; }
        public Builder jobTitle(String jobTitle) { this.jobTitle = jobTitle; return this; }
        public Builder jobStatus(JobStatus jobStatus) { this.jobStatus = jobStatus; return this; }
        public Builder expiredAttachments(List<JobAttachment> expiredAttachments) { this.expiredAttachments = expiredAttachments; return this; }
        public Builder expiredCount(int expiredCount) { this.expiredCount = expiredCount; return this; }

        public JobAttachmentAlertDTO build() {
            return new JobAttachmentAlertDTO(this);
        }
    }

    public Long getJobId() { return jobId; }
    public String getJobTitle() { return jobTitle; }
    public JobStatus getJobStatus() { return jobStatus; }
    public List<JobAttachment> getExpiredAttachments() { return expiredAttachments; }
    public int getExpiredCount() { return expiredCount; }
}