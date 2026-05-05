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
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PayoutPromoService {

    private final PayoutPromoRepository payoutPromoRepository;
    private final PayoutRepository payoutRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PayoutAuditService payoutAuditService;

    public PayoutPromoService(PayoutPromoRepository payoutPromoRepository,
                              PayoutRepository payoutRepository,
                              PromoCodeRepository promoCodeRepository,
                              PayoutAuditService payoutAuditService) {
        this.payoutPromoRepository = payoutPromoRepository;
        this.payoutRepository = payoutRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.payoutAuditService = payoutAuditService;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wallet-service::payout", key = "#request.payoutId"),
            @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
    })
    @Transactional
    public PayoutPromoDTO create(CreatePayoutPromoRequest request) {
        Payout payout = payoutRepository.findById(request.getPayoutId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));
        PromoCode promoCode = promoCodeRepository.findByIdForUpdate(request.getPromoCodeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PromoCode not found"));

        if (!Boolean.TRUE.equals(promoCode.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code is not active");
        }
        if (promoCode.getExpiryDate() != null && promoCode.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code has expired");
        }
        int currentUses = promoCode.getCurrentUses() == null ? 0 : promoCode.getCurrentUses();
        int maxUses = promoCode.getMaxUses() == null ? Integer.MAX_VALUE : promoCode.getMaxUses();
        if (currentUses >= maxUses) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code has reached maximum uses");
        }

        if (payoutPromoRepository.existsByPayout_IdAndPromoCode_Id(payout.getId(), promoCode.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo already applied to this payout");
        }

        promoCode.setCurrentUses(currentUses + 1);
        promoCodeRepository.save(promoCode);

        PayoutPromo pp = new PayoutPromo();
        pp.setPayout(payout);
        pp.setPromoCode(promoCode);
        pp.setDiscountApplied(request.getDiscountApplied() != null ? request.getDiscountApplied() : 0.0);
        PayoutPromo saved = payoutPromoRepository.save(pp);
        payoutAuditService.recordPayoutEvent(payout, PayoutAuditService.PAYOUT_PROMO_CREATED, payoutPromoDetails(saved));
        return new PayoutPromoDTO(saved);
    }

    @Transactional(readOnly = true)
    public PayoutPromoDTO getById(Long id) {
        PayoutPromo pp = payoutPromoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayoutPromo not found"));
        return new PayoutPromoDTO(pp);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wallet-service::payout", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
            @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
    })
    @Transactional
    public void delete(Long id) {
        PayoutPromo payoutPromo = payoutPromoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayoutPromo not found"));
        Payout payout = payoutPromo.getPayout();
        Map<String, Object> details = payoutPromoDetails(payoutPromo);
        payoutPromoRepository.deleteById(id);
        payoutAuditService.recordPayoutEvent(payout, PayoutAuditService.PAYOUT_PROMO_DELETED, details);
    }

    private Map<String, Object> payoutPromoDetails(PayoutPromo payoutPromo) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("entityType", "PayoutPromo");
        details.put("payoutPromoId", payoutPromo.getId());
        details.put("promoCodeId", payoutPromo.getPromoCode() == null ? null : payoutPromo.getPromoCode().getId());
        details.put("discountApplied", payoutPromo.getDiscountApplied());
        return details;
    }
}
