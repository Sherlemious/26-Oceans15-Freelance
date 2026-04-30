package com.team26.freelance.wallet.dto;

import com.team26.freelance.wallet.model.PayoutPromo;
import java.time.LocalDateTime;

public class PayoutPromoDTO {

    private Long id;
    private Long payoutId;
    private Long promoCodeId;
    private Double discountApplied;
    private LocalDateTime appliedAt;

    public PayoutPromoDTO(PayoutPromo pp) {
        this.id = pp.getId();
        this.payoutId = pp.getPayout().getId();
        this.promoCodeId = pp.getPromoCode().getId();
        this.discountApplied = pp.getDiscountApplied();
        this.appliedAt = pp.getAppliedAt();
    }

    public Long getId() { return id; }
    public Long getPayoutId() { return payoutId; }
    public Long getPromoCodeId() { return promoCodeId; }
    public Double getDiscountApplied() { return discountApplied; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}
