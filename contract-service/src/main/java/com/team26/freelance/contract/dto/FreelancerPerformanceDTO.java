package com.team26.freelance.contract.dto;

public class FreelancerPerformanceDTO {
    private final Long freelancerId;
    private final long totalContracts;
    private final double averageContractValue;
    private final double completionRate;
    private final double averageDurationDays;
    private final double totalEarnings;

    public FreelancerPerformanceDTO(Long freelancerId, long totalContracts, double averageContractValue,
                                    double completionRate, double averageDurationDays, double totalEarnings) {
        this.freelancerId = freelancerId;
        this.totalContracts = totalContracts;
        this.averageContractValue = averageContractValue;
        this.completionRate = completionRate;
        this.averageDurationDays = averageDurationDays;
        this.totalEarnings = totalEarnings;
    }

    // Getters
    public Long getFreelancerId() { return freelancerId; }
    public long getTotalContracts() { return totalContracts; }
    public double getAverageContractValue() { return averageContractValue; }
    public double getCompletionRate() { return completionRate; }
    public double getAverageDurationDays() { return averageDurationDays; }
    public double getTotalEarnings() { return totalEarnings; }
}
