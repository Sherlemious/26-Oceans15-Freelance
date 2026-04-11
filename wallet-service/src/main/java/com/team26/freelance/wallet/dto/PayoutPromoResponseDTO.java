package com.team26.freelance.wallet.dto;

import com.team26.freelance.wallet.model.DiscountType;
import com.team26.freelance.wallet.model.PayoutPromo;

import java.time.LocalDateTime;

public class PayoutPromoResponseDTO {

    private Long id;
    private Long promoCodeId;
    private String promoCode;
    private DiscountType discountType;
    private Double discountValue;
    private Double discountApplied;
    private LocalDateTime appliedAt;

    public PayoutPromoResponseDTO(PayoutPromo payoutPromo) {
        this.id = payoutPromo.getId();
        this.promoCodeId = payoutPromo.getPromoCode().getId();
        this.promoCode = payoutPromo.getPromoCode().getCode();
        this.discountType = payoutPromo.getPromoCode().getDiscountType();
        this.discountValue = payoutPromo.getPromoCode().getDiscountValue();
        this.discountApplied = payoutPromo.getDiscountApplied();
        this.appliedAt = payoutPromo.getAppliedAt();
    }

    public Long getId() {
        return id;
    }

    public Long getPromoCodeId() {
        return promoCodeId;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public Double getDiscountApplied() {
        return discountApplied;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }
}
