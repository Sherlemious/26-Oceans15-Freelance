package com.team26.freelance.contract.model.cassandra;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@PrimaryKeyClass
public class ContractMilestoneEventKey implements Serializable {

    @PrimaryKeyColumn(name = "contract_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    private Long contractId;

    @PrimaryKeyColumn(name = "timestamp", type = PrimaryKeyType.CLUSTERED, ordinal = 1, ordering = Ordering.DESCENDING)
    private LocalDateTime timestamp;

    public ContractMilestoneEventKey() {
    }

    public ContractMilestoneEventKey(Long contractId, LocalDateTime timestamp) {
        this.contractId = contractId;
        this.timestamp = timestamp;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ContractMilestoneEventKey that = (ContractMilestoneEventKey) o;
        return Objects.equals(contractId, that.contractId) && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractId, timestamp);
    }
}