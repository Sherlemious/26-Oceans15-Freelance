package com.team26.freelance.proposal.dto;

public class FeeEstimateRequest {
    private double bidAmount;
    private int estimatedDays;

    public double getBidAmount() { return bidAmount; }
    public void setBidAmount(double bidAmount) { this.bidAmount = bidAmount; }
    public int getEstimatedDays() { return estimatedDays; }
    public void setEstimatedDays(int estimatedDays) { this.estimatedDays = estimatedDays; }
}