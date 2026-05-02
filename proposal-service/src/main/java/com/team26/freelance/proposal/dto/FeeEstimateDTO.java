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

        private double bidAmount;
        private double platformFee;
        private double freelancerPayout;
        private double feePercentage;
        private double estimatedDailyRate;

        public Builder withBidAmount(double bidAmount) {
            this.bidAmount = bidAmount;
            return this;
        }

        public Builder withPlatformFee(double platformFee) {
            this.platformFee = platformFee;
            return this;
        }

        public Builder withFreelancerPayout(double freelancerPayout) {
            this.freelancerPayout = freelancerPayout;
            return this;
        }

        public Builder withFeePercentage(double feePercentage) {
            this.feePercentage = feePercentage;
            return this;
        }

        public Builder withEstimatedDailyRate(double estimatedDailyRate) {
            this.estimatedDailyRate = estimatedDailyRate;
            return this;
        }

        public FeeEstimateDTO build() {
            FeeEstimateDTO dto = new FeeEstimateDTO();
            dto.bidAmount = this.bidAmount;
            dto.platformFee = this.platformFee;
            dto.freelancerPayout = this.freelancerPayout;
            dto.feePercentage = this.feePercentage;
            dto.estimatedDailyRate = this.estimatedDailyRate;
            return dto;
        }
    }
}