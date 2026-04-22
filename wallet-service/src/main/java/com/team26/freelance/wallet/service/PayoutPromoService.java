package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.CreatePayoutPromoRequest;
import com.team26.freelance.wallet.dto.PayoutPromoDTO;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutPromo;
import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.repository.PayoutPromoRepository;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.repository.PromoCodeRepository;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PayoutPromoService {

    private final PayoutPromoRepository payoutPromoRepository;
    private final PayoutRepository payoutRepository;
    private final PromoCodeRepository promoCodeRepository;

    public PayoutPromoService(PayoutPromoRepository payoutPromoRepository,
                              PayoutRepository payoutRepository,
                              PromoCodeRepository promoCodeRepository) {
        this.payoutPromoRepository = payoutPromoRepository;
        this.payoutRepository = payoutRepository;
        this.promoCodeRepository = promoCodeRepository;
    }

    @Transactional
    public PayoutPromoDTO create(CreatePayoutPromoRequest request) {
        Payout payout = payoutRepository.findById(request.getPayoutId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));
        PromoCode promoCode = promoCodeRepository.findById(request.getPromoCodeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PromoCode not found"));

        if (payoutPromoRepository.existsByPayout_IdAndPromoCode_Id(payout.getId(), promoCode.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo already applied to this payout");
        }

        PayoutPromo pp = new PayoutPromo();
        pp.setPayout(payout);
        pp.setPromoCode(promoCode);
        pp.setDiscountApplied(request.getDiscountApplied() != null ? request.getDiscountApplied() : 0.0);
        pp.setAppliedAt(LocalDateTime.now());
        PayoutPromo saved = payoutPromoRepository.save(pp);
        return new PayoutPromoDTO(saved);
    }

    @Transactional(readOnly = true)
    public PayoutPromoDTO getById(Long id) {
        PayoutPromo pp = payoutPromoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayoutPromo not found"));
        return new PayoutPromoDTO(pp);
    }

    @Transactional
    public void delete(Long id) {
        payoutPromoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayoutPromo not found"));
        payoutPromoRepository.deleteById(id);
    }
}
