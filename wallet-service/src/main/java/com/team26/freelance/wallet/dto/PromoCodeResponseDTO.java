package com.team26.freelance.wallet.dto;

import com.team26.freelance.wallet.model.DiscountType;
import com.team26.freelance.wallet.model.PromoCode;
import java.time.LocalDateTime;
import java.util.Map;

public class PromoCodeResponseDTO {

    private Long id;
    private String code;
    private DiscountType discountType;
    private Double discountValue;
    private Integer maxUses;
    private Integer currentUses;
    private LocalDateTime expiryDate;
    private Boolean active;
    private Map<String, Object> metadata;

    public PromoCodeResponseDTO(PromoCode promoCode) {
        this.id = promoCode.getId();
        this.code = promoCode.getCode();
        this.discountType = promoCode.getDiscountType();
        this.discountValue = promoCode.getDiscountValue();
        this.maxUses = promoCode.getMaxUses();
        this.currentUses = promoCode.getCurrentUses();
        this.expiryDate = promoCode.getExpiryDate();
        this.active = promoCode.getActive();
        this.metadata = promoCode.getMetadata();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public Integer getCurrentUses() {
        return currentUses;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public Boolean getActive() {
        return active;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
