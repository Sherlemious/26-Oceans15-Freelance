package com.team26.freelance.contract.dto;

import java.time.LocalDateTime;

public class MilestoneTrackingResponse {

    private Long contractId;
    private LocalDateTime timestamp;
    private Integer milestoneOrder;
    private String status;
    private String recordedBy;
    private String notes;

    public MilestoneTrackingResponse(Long contractId, LocalDateTime timestamp, Integer milestoneOrder, String status,
            String recordedBy, String notes) {
        this.contractId = contractId;
        this.timestamp = timestamp;
        this.milestoneOrder = milestoneOrder;
        this.status = status;
        this.recordedBy = recordedBy;
        this.notes = notes;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
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