package com.team26.freelance.wallet.dto;

import java.util.Map;

public class FreelancerPayoutSummaryDTO {

    private Long freelancerId;
    private long totalPayouts;
    private double totalAmount;
    private Map<String, Double> methodBreakdown;

    public FreelancerPayoutSummaryDTO() {
    }

    private FreelancerPayoutSummaryDTO(Builder builder) {
        this.freelancerId = builder.freelancerId;
        this.totalPayouts = builder.totalPayouts;
        this.totalAmount = builder.totalAmount;
        this.methodBreakdown = builder.methodBreakdown;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getFreelancerId() {
        return freelancerId;
    }

    public void setFreelancerId(Long freelancerId) {
        this.freelancerId = freelancerId;
    }

    public long getTotalPayouts() {
        return totalPayouts;
    }

    public void setTotalPayouts(long totalPayouts) {
        this.totalPayouts = totalPayouts;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Map<String, Double> getMethodBreakdown() {
        return methodBreakdown;
    }

    public void setMethodBreakdown(Map<String, Double> methodBreakdown) {
        this.methodBreakdown = methodBreakdown;
    }

    public static class Builder {
        private Long freelancerId;
        private long totalPayouts;
        private double totalAmount;
        private Map<String, Double> methodBreakdown;

        public Builder freelancerId(Long freelancerId) {
            this.freelancerId = freelancerId;
            return this;
        }

        public Builder totalPayouts(long totalPayouts) {
            this.totalPayouts = totalPayouts;
            return this;
        }

        public Builder totalAmount(double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder methodBreakdown(Map<String, Double> methodBreakdown) {
            this.methodBreakdown = methodBreakdown;
            return this;
        }

        public FreelancerPayoutSummaryDTO build() {
            return new FreelancerPayoutSummaryDTO(this);
        }
    }
}