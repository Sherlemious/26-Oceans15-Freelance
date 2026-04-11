package com.team26.freelance.wallet.dto;

import java.time.LocalDateTime;

public class AppliedPromoCodeDTO {

    private String promoCode;
    private String discountType;
    private Double discountApplied;
    private LocalDateTime appliedAt;

    public AppliedPromoCodeDTO() {
    }

    public AppliedPromoCodeDTO(String promoCode, String discountType, Double discountApplied, LocalDateTime appliedAt) {
        this.promoCode = promoCode;
        this.discountType = discountType;
        this.discountApplied = discountApplied;
        this.appliedAt = appliedAt;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public Double getDiscountApplied() {
        return discountApplied;
    }

    public void setDiscountApplied(Double discountApplied) {
        this.discountApplied = discountApplied;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}