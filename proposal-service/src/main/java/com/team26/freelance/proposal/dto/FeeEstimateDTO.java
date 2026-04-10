package com.team26.freelance.proposal.dto;

public class FeeEstimateDTO {
    private double bidAmount;
    private double platformFee;
    private double freelancerPayout;
    private double feePercentage;
    private double estimatedDailyRate;

    public FeeEstimateDTO(double bidAmount, double platformFee,
                          double freelancerPayout, double feePercentage,
                          double estimatedDailyRate) {
        this.bidAmount = bidAmount;
        this.platformFee = platformFee;
        this.freelancerPayout = freelancerPayout;
        this.feePercentage = feePercentage;
        this.estimatedDailyRate = estimatedDailyRate;
    }

    // Getters
    public double getBidAmount() { return bidAmount; }
    public double getPlatformFee() { return platformFee; }
    public double getFreelancerPayout() { return freelancerPayout; }
    public double getFeePercentage() { return feePercentage; }
    public double getEstimatedDailyRate() { return estimatedDailyRate; }
}