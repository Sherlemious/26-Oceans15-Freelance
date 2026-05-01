package com.team26.freelance.contract.model.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;

@Table("contract_milestone_events")
public class ContractMilestoneEvent {

    @PrimaryKey
    private ContractMilestoneEventKey key;

    @Column("milestone_order")
    private Integer milestoneOrder;

    @Column("status")
    private String status;

    @Column("recorded_by")
    private String recordedBy;

    @Column("notes")
    private String notes;

    public ContractMilestoneEvent() {
    }

    public ContractMilestoneEvent(ContractMilestoneEventKey key, Integer milestoneOrder, String status,
            String recordedBy, String notes) {
        this.key = key;
        this.milestoneOrder = milestoneOrder;
        this.status = status;
        this.recordedBy = recordedBy;
        this.notes = notes;
    }

    public ContractMilestoneEventKey getKey() {
        return key;
    }

    public void setKey(ContractMilestoneEventKey key) {
        this.key = key;
    }

    public Long getContractId() {
        return key == null ? null : key.getContractId();
    }

    public LocalDateTime getTimestamp() {
        return key == null ? null : key.getTimestamp();
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