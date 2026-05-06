package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.repository.PromoCodeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PayoutAuditService payoutAuditService;

    public PromoCodeService(PromoCodeRepository promoCodeRepository,
                            PayoutAuditService payoutAuditService) {
        this.promoCodeRepository = promoCodeRepository;
        this.payoutAuditService = payoutAuditService;
    }

    public List<PromoCode> getAll() {
        return promoCodeRepository.findAll();
    }

    public PromoCode getById(Long id) {
        return promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PromoCode not found"));
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
    public PromoCode create(PromoCode promoCode) {
        promoCode.setId(null);
        promoCode.setCurrentUses(0);
        PromoCode saved = promoCodeRepository.save(promoCode);
        payoutAuditService.recordGenericEvent(PayoutAuditService.PROMO_CODE_CREATED, promoCodeDetails(saved));
        return saved;
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
    public PromoCode update(Long id, PromoCode updated) {
        PromoCode existing = getById(id);
        existing.setCode(updated.getCode());
        existing.setDiscountType(updated.getDiscountType());
        existing.setDiscountValue(updated.getDiscountValue());
        existing.setMaxUses(updated.getMaxUses());
        existing.setExpiryDate(updated.getExpiryDate());
        existing.setActive(updated.getActive());
        if (updated.getMetadata() != null) {
            existing.setMetadata(updated.getMetadata());
        }
        PromoCode saved = promoCodeRepository.save(existing);
        payoutAuditService.recordGenericEvent(PayoutAuditService.PROMO_CODE_UPDATED, promoCodeDetails(saved));
        return saved;
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
        PromoCode existing = getById(id);
        Map<String, Object> details = promoCodeDetails(existing);
        promoCodeRepository.delete(existing);
        payoutAuditService.recordGenericEvent(PayoutAuditService.PROMO_CODE_DELETED, details);
    }

    private Map<String, Object> promoCodeDetails(PromoCode promoCode) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("entityType", "PromoCode");
        details.put("promoCodeId", promoCode.getId());
        details.put("code", promoCode.getCode());
        details.put("discountType", promoCode.getDiscountType() == null ? null : promoCode.getDiscountType().name());
        details.put("active", promoCode.getActive());
        return details;
    }
}
