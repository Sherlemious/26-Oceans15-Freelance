package com.team26.freelance.wallet.dto;

public class CreatePayoutPromoRequest {

    private Long payoutId;
    private Long promoCodeId;
    private Double discountApplied;

    public Long getPayoutId() { return payoutId; }
    public void setPayoutId(Long payoutId) { this.payoutId = payoutId; }

    public Long getPromoCodeId() { return promoCodeId; }
    public void setPromoCodeId(Long promoCodeId) { this.promoCodeId = promoCodeId; }

    public Double getDiscountApplied() { return discountApplied; }
    public void setDiscountApplied(Double discountApplied) { this.discountApplied = discountApplied; }
}
