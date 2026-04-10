package com.team26.freelance.wallet.dto;

import java.util.Map;

public class FreelancerPayoutSummaryDTO {

    private Long freelancerId;
    private long totalPayouts;
    private double totalAmount;
    private Map<String, Double> methodBreakdown;

    public FreelancerPayoutSummaryDTO(Long freelancerId, long totalPayouts,
                                      double totalAmount, Map<String, Double> methodBreakdown) {
        this.freelancerId = freelancerId;
        this.totalPayouts = totalPayouts;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }

    public Long getFreelancerId() { return freelancerId; }
    public long getTotalPayouts() { return totalPayouts; }
    public double getTotalAmount() { return totalAmount; }
    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
}
