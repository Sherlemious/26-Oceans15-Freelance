package com.team26.freelance.contract.dto;

public class FreelancerPerformanceDTO {
    private Long freelancerId;
    private long totalContracts;
    private double averageContractValue;
    private double completionRate;
    private double averageDurationDays;
    private double totalEarnings;

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
