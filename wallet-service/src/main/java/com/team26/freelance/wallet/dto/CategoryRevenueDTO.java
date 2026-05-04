package com.team26.freelance.wallet.dto;

public class CategoryRevenueDTO {

    private String category;
    private Double netPayoutRevenue;
    private Double platformFeeRevenue;
    private Double totalRevenue;
    private Long payoutCount;

    public CategoryRevenueDTO() {
    }

    private CategoryRevenueDTO(Builder builder) {
        this.category = builder.category;
        this.netPayoutRevenue = builder.netPayoutRevenue;
        this.platformFeeRevenue = builder.platformFeeRevenue;
        this.totalRevenue = builder.totalRevenue;
        this.payoutCount = builder.payoutCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getNetPayoutRevenue() {
        return netPayoutRevenue;
    }

    public void setNetPayoutRevenue(Double netPayoutRevenue) {
        this.netPayoutRevenue = netPayoutRevenue;
    }

    public Double getPlatformFeeRevenue() {
        return platformFeeRevenue;
    }

    public void setPlatformFeeRevenue(Double platformFeeRevenue) {
        this.platformFeeRevenue = platformFeeRevenue;
    }

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(Double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public Long getPayoutCount() {
        return payoutCount;
    }

    public void setPayoutCount(Long payoutCount) {
        this.payoutCount = payoutCount;
    }

    public static class Builder {
        private String category;
        private Double netPayoutRevenue;
        private Double platformFeeRevenue;
        private Double totalRevenue;
        private Long payoutCount;

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder netPayoutRevenue(Double netPayoutRevenue) {
            this.netPayoutRevenue = netPayoutRevenue;
            return this;
        }

        public Builder platformFeeRevenue(Double platformFeeRevenue) {
            this.platformFeeRevenue = platformFeeRevenue;
            return this;
        }

        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder payoutCount(Long payoutCount) {
            this.payoutCount = payoutCount;
            return this;
        }

        public CategoryRevenueDTO build() {
            return new CategoryRevenueDTO(this);
        }
    }
}