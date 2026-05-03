// proposal-service/src/main/java/com/team26/freelance/proposal/model/ProposalEvent.java

package com.team26.freelance.proposal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "proposal_events")
public class ProposalEvent {

    @Id
    private String id;

    private Long proposalId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public ProposalEvent() {}

    public ProposalEvent(Long proposalId, String action,
                         LocalDateTime timestamp, Map<String, Object> details) {
        this.proposalId = proposalId;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    public String getId() { return id; }
    public Long getProposalId() { return proposalId; }
    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return details; }

    public void setId(String id) { this.id = id; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}