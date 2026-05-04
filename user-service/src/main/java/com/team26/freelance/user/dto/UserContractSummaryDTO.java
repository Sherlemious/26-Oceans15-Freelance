package com.team26.freelance.user.dto;

import java.math.BigDecimal;

public class UserContractSummaryDTO {

    private Long userId;
    private String name;
    private Long totalContracts;
    private Long completedContracts;
    private Long terminatedContracts;
    private BigDecimal totalEarnings;
    private BigDecimal averageContractValue;

    public UserContractSummaryDTO() {
    }

    public UserContractSummaryDTO(Long userId,
                                  String name,
                                  Long totalContracts,
                                  Long completedContracts,
                                  Long terminatedContracts,
                                  BigDecimal totalEarnings,
                                  BigDecimal averageContractValue) {
        this.userId = userId;
        this.name = name;
        this.totalContracts = totalContracts;
        this.completedContracts = completedContracts;
        this.terminatedContracts = terminatedContracts;
        this.totalEarnings = totalEarnings;
        this.averageContractValue = averageContractValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTotalContracts() {
        return totalContracts;
    }

    public void setTotalContracts(Long totalContracts) {
        this.totalContracts = totalContracts;
    }

    public Long getCompletedContracts() {
        return completedContracts;
    }

    public void setCompletedContracts(Long completedContracts) {
        this.completedContracts = completedContracts;
    }

    public Long getTerminatedContracts() {
        return terminatedContracts;
    }

    public void setTerminatedContracts(Long terminatedContracts) {
        this.terminatedContracts = terminatedContracts;
    }

    public BigDecimal getTotalEarnings() {
        return totalEarnings;
    }

    public void setTotalEarnings(BigDecimal totalEarnings) {
        this.totalEarnings = totalEarnings;
    }

    public BigDecimal getAverageContractValue() {
        return averageContractValue;
    }

    public void setAverageContractValue(BigDecimal averageContractValue) {
        this.averageContractValue = averageContractValue;
    }

    public static class Builder {
        private Long userId;
        private String name;
        private Long totalContracts;
        private Long completedContracts;
        private Long terminatedContracts;
        private BigDecimal totalEarnings;
        private BigDecimal averageContractValue;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder totalContracts(Long totalContracts) {
            this.totalContracts = totalContracts;
            return this;
        }

        public Builder completedContracts(Long completedContracts) {
            this.completedContracts = completedContracts;
            return this;
        }

        public Builder terminatedContracts(Long terminatedContracts) {
            this.terminatedContracts = terminatedContracts;
            return this;
        }

        public Builder totalEarnings(BigDecimal totalEarnings) {
            this.totalEarnings = totalEarnings;
            return this;
        }

        public Builder averageContractValue(BigDecimal averageContractValue) {
            this.averageContractValue = averageContractValue;
            return this;
        }

        public UserContractSummaryDTO build() {
            return new UserContractSummaryDTO(
                    userId,
                    name,
                    totalContracts,
                    completedContracts,
                    terminatedContracts,
                    totalEarnings,
                    averageContractValue
            );
        }
    }
}
