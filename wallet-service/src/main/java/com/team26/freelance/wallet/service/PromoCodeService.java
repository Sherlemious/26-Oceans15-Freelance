package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.repository.PromoCodeRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    public List<PromoCode> getAll() {
        return promoCodeRepository.findAll();
    }

    public PromoCode getById(Long id) {
        return promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PromoCode not found"));
    }

    @Transactional
    public PromoCode create(PromoCode promoCode) {
        promoCode.setId(null);
        promoCode.setCurrentUses(0);
        return promoCodeRepository.save(promoCode);
    }

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
        return promoCodeRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        promoCodeRepository.deleteById(id);
    }
}
