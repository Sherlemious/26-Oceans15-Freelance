package com.team26.freelance.proposal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "proposal_events")
public class ProposalEvent implements MongoEvent {

    @Id
    private String id;
    private Long proposalId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public ProposalEvent() {}

    public ProposalEvent(Map<String, Object> params) {
        Object rawId = params.get("proposalId");
        this.proposalId = rawId instanceof Number n ? n.longValue() : null;
        this.action = (String) params.get("action");
        Object ts = params.get("timestamp");
        this.timestamp = ts instanceof LocalDateTime ldt ? ldt : LocalDateTime.now();
        Object det = params.get("details");
        this.details = det instanceof Map<?, ?> m ? new HashMap<>((Map<String, Object>) m) : new HashMap<>(params);
    }

    @Override
    public String getId() { return id; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String getAction() { return action; }

    @Override
    public Map<String, Object> getDetails() { return details; }

    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public void setId(String id) { this.id = id; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
