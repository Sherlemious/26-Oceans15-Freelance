package com.team26.freelance.contract.dto;

import java.time.Instant;

public class ContractMilestoneDTO {
    private Instant timestamp;
    private Integer milestoneOrder;
    private String status;
    private String recordedBy;
    private String notes;

    public ContractMilestoneDTO() {
    }

    public ContractMilestoneDTO(Instant timestamp, Integer milestoneOrder, String status, String recordedBy, String notes) {
        this.timestamp = timestamp;
        this.milestoneOrder = milestoneOrder;
        this.status = status;
        this.recordedBy = recordedBy;
        this.notes = notes;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getMilestoneOrder() {
        return milestoneOrder;
    }

    public void setMilestoneOrder(Integer milestoneOrder) {
        this.milestoneOrder = milestoneOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
