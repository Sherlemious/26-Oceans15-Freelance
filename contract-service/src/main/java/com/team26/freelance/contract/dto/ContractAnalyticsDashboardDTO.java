package com.team26.freelance.contract.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team26.freelance.contract.model.ContractStatus;

import java.util.EnumMap;
import java.util.Map;

public class ContractAnalyticsDashboardDTO {
    private final long totalContracts;
    private final Map<ContractStatus, Long> contractsByStatus;
    private final double averageContractBudget;
    private final double completionRate;
    private final long contractsCompletedLast30Days;

    @JsonCreator
    public ContractAnalyticsDashboardDTO(
            @JsonProperty("totalContracts") long totalContracts,
            @JsonProperty("contractsByStatus") Map<ContractStatus, Long> contractsByStatus,
            @JsonProperty("averageContractBudget") double averageContractBudget,
            @JsonProperty("completionRate") double completionRate,
            @JsonProperty("contractsCompletedLast30Days") long contractsCompletedLast30Days) {
        this.totalContracts = totalContracts;
        this.contractsByStatus = normalizeStatusCounts(contractsByStatus);
        this.averageContractBudget = averageContractBudget;
        this.completionRate = completionRate;
        this.contractsCompletedLast30Days = contractsCompletedLast30Days;
    }

    private static Map<ContractStatus, Long> normalizeStatusCounts(Map<ContractStatus, Long> contractsByStatus) {
        Map<ContractStatus, Long> normalized = new EnumMap<>(ContractStatus.class);
        for (ContractStatus status : ContractStatus.values()) {
            normalized.put(status, 0L);
        }
        if (contractsByStatus != null) {
            contractsByStatus.forEach((status, count) -> {
                if (status != null) {
                    normalized.put(status, count != null ? count : 0L);
                }
            });
        }
        return normalized;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long totalContracts;
        private Map<ContractStatus, Long> contractsByStatus;
        private double averageContractBudget;
        private double completionRate;
        private long contractsCompletedLast30Days;

        private Builder() {
        }

        public Builder totalContracts(long totalContracts) {
            this.totalContracts = totalContracts;
            return this;
        }

        public Builder contractsByStatus(Map<ContractStatus, Long> contractsByStatus) {
            this.contractsByStatus = contractsByStatus;
            return this;
        }

        public Builder averageContractBudget(double averageContractBudget) {
            this.averageContractBudget = averageContractBudget;
            return this;
        }

        public Builder completionRate(double completionRate) {
            this.completionRate = completionRate;
            return this;
        }

        public Builder contractsCompletedLast30Days(long contractsCompletedLast30Days) {
            this.contractsCompletedLast30Days = contractsCompletedLast30Days;
            return this;
        }

        public ContractAnalyticsDashboardDTO build() {
            return new ContractAnalyticsDashboardDTO(totalContracts, contractsByStatus, averageContractBudget,
                    completionRate, contractsCompletedLast30Days);
        }
    }

    public long getTotalContracts() {
        return totalContracts;
    }

    public Map<ContractStatus, Long> getContractsByStatus() {
        return contractsByStatus;
    }

    public double getAverageContractBudget() {
        return averageContractBudget;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public long getContractsCompletedLast30Days() {
        return contractsCompletedLast30Days;
    }
}
