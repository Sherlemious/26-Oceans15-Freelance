package com.team26.freelance.wallet.dto;

public class PromoCodeUsageDTO {

    private Long promoCodeId;
    private String code;
    private String discountType;
    private Double discountValue;
    private Integer timesUsed;
    private Double totalDiscountGiven;
    private Boolean active;
    private Boolean expired;

    public PromoCodeUsageDTO() {
    }

    private PromoCodeUsageDTO(Builder builder) {
        this.promoCodeId = builder.promoCodeId;
        this.code = builder.code;
        this.discountType = builder.discountType;
        this.discountValue = builder.discountValue;
        this.timesUsed = builder.timesUsed;
        this.totalDiscountGiven = builder.totalDiscountGiven;
        this.active = builder.active;
        this.expired = builder.expired;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getPromoCodeId() {
        return promoCodeId;
    }

    public void setPromoCodeId(Long promoCodeId) {
        this.promoCodeId = promoCodeId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(Double discountValue) {
        this.discountValue = discountValue;
    }

    public Integer getTimesUsed() {
        return timesUsed;
    }

    public void setTimesUsed(Integer timesUsed) {
        this.timesUsed = timesUsed;
    }

    public Double getTotalDiscountGiven() {
        return totalDiscountGiven;
    }

    public void setTotalDiscountGiven(Double totalDiscountGiven) {
        this.totalDiscountGiven = totalDiscountGiven;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getExpired() {
        return expired;
    }

    public void setExpired(Boolean expired) {
        this.expired = expired;
    }

    public static class Builder {
        private Long promoCodeId;
        private String code;
        private String discountType;
        private Double discountValue;
        private Integer timesUsed;
        private Double totalDiscountGiven;
        private Boolean active;
        private Boolean expired;

        public Builder promoCodeId(Long promoCodeId) {
            this.promoCodeId = promoCodeId;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder discountType(String discountType) {
            this.discountType = discountType;
            return this;
        }

        public Builder discountValue(Double discountValue) {
            this.discountValue = discountValue;
            return this;
        }

        public Builder timesUsed(Integer timesUsed) {
            this.timesUsed = timesUsed;
            return this;
        }

        public Builder totalDiscountGiven(Double totalDiscountGiven) {
            this.totalDiscountGiven = totalDiscountGiven;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder expired(Boolean expired) {
            this.expired = expired;
            return this;
        }

        public PromoCodeUsageDTO build() {
            return new PromoCodeUsageDTO(this);
        }
    }
}