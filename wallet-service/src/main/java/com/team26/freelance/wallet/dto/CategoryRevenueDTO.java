package com.team26.freelance.wallet.dto;

public class CategoryRevenueDTO {

    private String jobCategory;
    private Double totalFees;
    private Double averageFee;
    private Long payoutCount;

    public CategoryRevenueDTO() {
    }

    private CategoryRevenueDTO(Builder builder) {
        this.jobCategory = builder.jobCategory;
        this.totalFees = builder.totalFees;
        this.averageFee = builder.averageFee;
        this.payoutCount = builder.payoutCount;
    }

    public String getJobCategory() {
        return jobCategory;
    }

    public void setJobCategory(String jobCategory) {
        this.jobCategory = jobCategory;
    }

    public Double getTotalFees() {
        return totalFees;
    }

    public void setTotalFees(Double totalFees) {
        this.totalFees = totalFees;
    }

    public Double getAverageFee() {
        return averageFee;
    }

    public void setAverageFee(Double averageFee) {
        this.averageFee = averageFee;
    }

    public Long getPayoutCount() {
        return payoutCount;
    }

    public void setPayoutCount(Long payoutCount) {
        this.payoutCount = payoutCount;
    }

    public static class Builder {
        private String jobCategory;
        private Double totalFees;
        private Double averageFee;
        private Long payoutCount;

        public Builder jobCategory(String jobCategory) {
            this.jobCategory = jobCategory;
            return this;
        }

        public Builder totalFees(Double totalFees) {
            this.totalFees = totalFees;
            return this;
        }

        public Builder averageFee(Double averageFee) {
            this.averageFee = averageFee;
            return this;
        }

        public Builder payoutCount(Long payoutCount) {
            this.payoutCount = payoutCount;
            this.payoutCount = payoutCount;
            return this;
        }

        public CategoryRevenueDTO build() {
            return new CategoryRevenueDTO(this);
        }
    }
}