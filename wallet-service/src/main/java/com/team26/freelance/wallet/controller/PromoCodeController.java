package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.PromoCodeRequestDTO;
import com.team26.freelance.wallet.dto.PromoCodeResponseDTO;
import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.service.PromoCodeService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promo-codes")
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    public PromoCodeController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @GetMapping
    public ResponseEntity<List<PromoCodeResponseDTO>> getAll() {
        return ResponseEntity.ok(
                promoCodeService.getAll().stream().map(PromoCodeResponseDTO::new).collect(Collectors.toList())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromoCodeResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new PromoCodeResponseDTO(promoCodeService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponseDTO> create(@RequestBody PromoCodeRequestDTO request) {
        PromoCode promoCode = mapToEntity(request);
        return ResponseEntity.status(201).body(new PromoCodeResponseDTO(promoCodeService.create(promoCode)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromoCodeResponseDTO> update(@PathVariable Long id, @RequestBody PromoCodeRequestDTO request) {
        PromoCode promoCode = mapToEntity(request);
        return ResponseEntity.ok(new PromoCodeResponseDTO(promoCodeService.update(id, promoCode)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promoCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private PromoCode mapToEntity(PromoCodeRequestDTO request) {
        PromoCode promoCode = new PromoCode();
        promoCode.setCode(request.getCode());
        promoCode.setDiscountType(request.getDiscountType());
        promoCode.setDiscountValue(request.getDiscountValue());
        promoCode.setMaxUses(request.getMaxUses());
        promoCode.setExpiryDate(request.getExpiryDate());
        promoCode.setActive(request.getActive());
        promoCode.setMetadata(request.getMetadata());
        return promoCode;
    }
}
