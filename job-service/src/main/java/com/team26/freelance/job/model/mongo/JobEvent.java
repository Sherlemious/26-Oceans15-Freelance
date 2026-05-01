package com.team26.freelance.job.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "job_events")
public class JobEvent implements MongoEvent {
    @Id
    private String id;

    private Long jobId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public JobEvent() {}

    public JobEvent(Long jobId, String action, Map<String, Object> details) {
        this.jobId = jobId;
        this.action = action;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public JobEvent(Map<String, Object> params) {
        Object rawId = params.get("jobId");
        this.jobId = rawId instanceof Number n ? n.longValue() : null;
        this.action = (String) params.get("action");
        Object ts = params.get("timestamp");
        this.timestamp = ts instanceof LocalDateTime ldt ? ldt : LocalDateTime.now();
        Object det = params.get("details");
        this.details = det instanceof Map<?, ?> m ? (Map<String, Object>) m : new java.util.HashMap<>(params);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    @Override
    public String toString() {
        return "JobEvent{" +
                "id='" + id + '\'' +
                ", jobId=" + jobId +
                ", action='" + action + '\'' +
                ", timestamp=" + timestamp +
                ", details=" + details +
                '}';
    }
}