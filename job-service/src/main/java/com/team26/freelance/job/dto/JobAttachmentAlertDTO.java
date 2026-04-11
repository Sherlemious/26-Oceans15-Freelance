package com.team26.freelance.job.dto;
import com.team26.freelance.job.model.JobAttachment;
import java.util.List;

public class JobAttachmentAlertDTO {

    private Long jobId;
    private String jobTitle;
    private List<JobAttachment> expiredAttachments;
    private int expiredCount;

    public JobAttachmentAlertDTO(Long jobId, String jobTitle, List<JobAttachment> expiredAttachments, int expiredCount) {
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.expiredAttachments = expiredAttachments;
        this.expiredCount = expiredCount;
    }

    public Long getJobId() { return jobId; }
    public String getJobTitle() { return jobTitle; }
    public List<JobAttachment> getExpiredAttachments() { return expiredAttachments; }
    public int getExpiredCount() { return expiredCount; }

}
