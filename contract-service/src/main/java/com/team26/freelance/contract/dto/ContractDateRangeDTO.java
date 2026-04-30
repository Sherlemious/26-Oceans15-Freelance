package com.team26.freelance.contract.dto;

import com.team26.freelance.contract.model.ContractStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for S4-F6: contracts retrieved within a date range.
 * Carries the fields a caller needs without exposing the full JPA entity.
 */
public class ContractDateRangeDTO {

    private Long id;
    private Long jobId;
    private Long freelancerId;
    private Long clientId;
    private Long proposalId;
    private Double agreedAmount;
    private ContractStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    public ContractDateRangeDTO(Long id, Long jobId, Long freelancerId, Long clientId,
            Long proposalId, Double agreedAmount, ContractStatus status,
            LocalDateTime startDate, LocalDateTime endDate,
            Map<String, Object> metadata, LocalDateTime createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.freelancerId = freelancerId;
        this.clientId = clientId;
        this.proposalId = proposalId;
        this.agreedAmount = agreedAmount;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    // Private no-arg constructor for Builder
    private ContractDateRangeDTO() {}

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private Long jobId;
        private Long freelancerId;
        private Long clientId;
        private Long proposalId;
        private Double agreedAmount;
        private ContractStatus status;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;

        private Builder() {}

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder jobId(Long jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder freelancerId(Long freelancerId) {
            this.freelancerId = freelancerId;
            return this;
        }

        public Builder clientId(Long clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder proposalId(Long proposalId) {
            this.proposalId = proposalId;
            return this;
        }

        public Builder agreedAmount(Double agreedAmount) {
            this.agreedAmount = agreedAmount;
            return this;
        }

        public Builder status(ContractStatus status) {
            this.status = status;
            return this;
        }

        public Builder startDate(LocalDateTime startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDateTime endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ContractDateRangeDTO build() {
            return new ContractDateRangeDTO(id, jobId, freelancerId, clientId, proposalId,
                    agreedAmount, status, startDate, endDate, metadata, createdAt);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public Long getFreelancerId() { return freelancerId; }
    public Long getClientId() { return clientId; }
    public Long getProposalId() { return proposalId; }
    public Double getAgreedAmount() { return agreedAmount; }
    public ContractStatus getStatus() { return status; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
