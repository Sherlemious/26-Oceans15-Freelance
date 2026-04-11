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
}
