package com.team26.freelance.proposal.dto;

public class FeeEstimateDTO {

    private double bidAmount;
    private double platformFee;
    private double freelancerPayout;
    private double feePercentage;
    private double estimatedDailyRate;

    private FeeEstimateDTO() {}

    public double getBidAmount() { return bidAmount; }
    public double getPlatformFee() { return platformFee; }
    public double getFreelancerPayout() { return freelancerPayout; }
    public double getFeePercentage() { return feePercentage; }
    public double getEstimatedDailyRate() { return estimatedDailyRate; }

    // ── Builder ────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final FeeEstimateDTO dto = new FeeEstimateDTO();

        public Builder withBidAmount(double bidAmount) {
            dto.bidAmount = bidAmount;
            return this;
        }

        public Builder withPlatformFee(double platformFee) {
            dto.platformFee = platformFee;
            return this;
        }

        public Builder withFreelancerPayout(double freelancerPayout) {
            dto.freelancerPayout = freelancerPayout;
            return this;
        }

        public Builder withFeePercentage(double feePercentage) {
            dto.feePercentage = feePercentage;
            return this;
        }

        public Builder withEstimatedDailyRate(double estimatedDailyRate) {
            dto.estimatedDailyRate = estimatedDailyRate;
            return this;
        }

        public FeeEstimateDTO build() {
            return dto;
        }
    }
}