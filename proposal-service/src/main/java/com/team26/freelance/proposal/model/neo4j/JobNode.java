package com.team26.freelance.proposal.model.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Job")
public class JobNode {
    @Id
    private Long jobId;
    private String title;
    private String category;

    public JobNode() {}

    public JobNode(Long jobId, String title, String category) {
        this.jobId = jobId;
        this.title = title;
        this.category = category;
    }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}