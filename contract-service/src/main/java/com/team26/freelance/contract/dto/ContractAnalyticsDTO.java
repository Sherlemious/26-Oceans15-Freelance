package com.team26.freelance.contract.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class ContractAnalyticsDTO {

    private long totalContracts;
    private double averageContractValue;
    private double completionRate;
    private double averageContractDurationDays;
    private Map<String, Long> contractsByStatus = new LinkedHashMap<>();

    public ContractAnalyticsDTO() {
    }

    private ContractAnalyticsDTO(Builder builder) {
        this.totalContracts = builder.totalContracts;
        this.averageContractValue = builder.averageContractValue;
        this.completionRate = builder.completionRate;
        this.averageContractDurationDays = builder.averageContractDurationDays;
        this.contractsByStatus = new LinkedHashMap<>(builder.contractsByStatus);
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getTotalContracts() {
        return totalContracts;
    }

    public void setTotalContracts(long totalContracts) {
        this.totalContracts = totalContracts;
    }

    public double getAverageContractValue() {
        return averageContractValue;
    }

    public void setAverageContractValue(double averageContractValue) {
        this.averageContractValue = averageContractValue;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    public double getAverageContractDurationDays() {
        return averageContractDurationDays;
    }

    public void setAverageContractDurationDays(double averageContractDurationDays) {
        this.averageContractDurationDays = averageContractDurationDays;
    }

    public Map<String, Long> getContractsByStatus() {
        return contractsByStatus;
    }

    public void setContractsByStatus(Map<String, Long> contractsByStatus) {
        this.contractsByStatus = contractsByStatus == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(contractsByStatus);
    }

    public static final class Builder {
        private long totalContracts;
        private double averageContractValue;
        private double completionRate;
        private double averageContractDurationDays;
        private Map<String, Long> contractsByStatus = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder totalContracts(long totalContracts) {
            this.totalContracts = totalContracts;
            return this;
        }

        public Builder averageContractValue(double averageContractValue) {
            this.averageContractValue = averageContractValue;
            return this;
        }

        public Builder completionRate(double completionRate) {
            this.completionRate = completionRate;
            return this;
        }

        public Builder averageContractDurationDays(double averageContractDurationDays) {
            this.averageContractDurationDays = averageContractDurationDays;
            return this;
        }

        public Builder contractsByStatus(Map<String, Long> contractsByStatus) {
            this.contractsByStatus = contractsByStatus == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(contractsByStatus);
            return this;
        }

        public ContractAnalyticsDTO build() {
            return new ContractAnalyticsDTO(this);
        }
    }
}
