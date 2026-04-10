package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.PayoutResponseDTO;
import com.team26.freelance.wallet.model.DiscountType;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutPromo;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.repository.PayoutPromoRepository;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.repository.PromoCodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PayoutPromoRepository payoutPromoRepository;

    public PayoutService(
            PayoutRepository payoutRepository,
            PromoCodeRepository promoCodeRepository,
            PayoutPromoRepository payoutPromoRepository
    ) {
        this.payoutRepository = payoutRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.payoutPromoRepository = payoutPromoRepository;
    }

    @Transactional
    public PayoutResponseDTO applyPromoToPayout(Long payoutId, Long promoCodeId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));

        PromoCode promoCode = promoCodeRepository.findById(promoCodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo code not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code can only be applied to pending payouts");
        }

        if (!Boolean.TRUE.equals(promoCode.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code is inactive");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!promoCode.getExpiryDate().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code is expired");
        }

        int currentUses = promoCode.getCurrentUses() == null ? 0 : promoCode.getCurrentUses();
        if (currentUses >= promoCode.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code max uses reached");
        }

        if (payoutPromoRepository.existsByPayout_IdAndPromoCode_Id(payoutId, promoCodeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code is already applied to this payout");
        }

        PayoutPromo payoutPromo = new PayoutPromo();
        payoutPromo.setPayout(payout);
        payoutPromo.setPromoCode(promoCode);
        payoutPromo.setDiscountApplied(calculateDiscountApplied(payout, promoCode));
        payoutPromo.setAppliedAt(now);
        payoutPromoRepository.save(payoutPromo);

        promoCode.setCurrentUses(currentUses + 1);
        promoCodeRepository.save(promoCode);

        Payout updatedPayout = payoutRepository.findByIdWithPromos(payoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));

        return new PayoutResponseDTO(updatedPayout);
    }

    private double calculateDiscountApplied(Payout payout, PromoCode promoCode) {
        double discountApplied;

        if (promoCode.getDiscountType() == DiscountType.PERCENTAGE) {
            discountApplied = payout.getAmount() * promoCode.getDiscountValue() / 100.0;
        } else {
            discountApplied = promoCode.getDiscountValue();
        }

        return Math.min(discountApplied, payout.getAmount());
    }
}
