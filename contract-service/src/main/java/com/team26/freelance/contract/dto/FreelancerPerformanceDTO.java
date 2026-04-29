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

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long freelancerId;
        private long totalContracts;
        private double averageContractValue;
        private double completionRate;
        private double averageDurationDays;
        private double totalEarnings;

        private Builder() {}

        public Builder freelancerId(Long freelancerId) {
            this.freelancerId = freelancerId;
            return this;
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

        public Builder averageDurationDays(double averageDurationDays) {
            this.averageDurationDays = averageDurationDays;
            return this;
        }

        public Builder totalEarnings(double totalEarnings) {
            this.totalEarnings = totalEarnings;
            return this;
        }

        public FreelancerPerformanceDTO build() {
            return new FreelancerPerformanceDTO(freelancerId, totalContracts, averageContractValue,
                    completionRate, averageDurationDays, totalEarnings);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getFreelancerId() { return freelancerId; }
    public long getTotalContracts() { return totalContracts; }
    public double getAverageContractValue() { return averageContractValue; }
    public double getCompletionRate() { return completionRate; }
    public double getAverageDurationDays() { return averageDurationDays; }
    public double getTotalEarnings() { return totalEarnings; }
}
