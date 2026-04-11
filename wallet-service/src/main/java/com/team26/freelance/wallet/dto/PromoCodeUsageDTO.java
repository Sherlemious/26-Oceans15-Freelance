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
}