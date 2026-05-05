package com.team26.freelance.job.event;

public class JobIndexEvent {
    private final Long jobId;
    private final String source;
    private final boolean delete;

    public JobIndexEvent(Long jobId, String source, boolean delete) {
        this.jobId = jobId;
        this.source = source;
        this.delete = delete;
    }

    public Long getJobId() { return jobId; }
    public String getSource() { return source; }
    public boolean isDelete() { return delete; }
}