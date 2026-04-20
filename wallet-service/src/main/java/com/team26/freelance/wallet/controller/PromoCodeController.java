package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.service.PromoCodeService;
import java.util.List;
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
    public ResponseEntity<List<PromoCode>> getAll() {
        return ResponseEntity.ok(promoCodeService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromoCode> getById(@PathVariable Long id) {
        return ResponseEntity.ok(promoCodeService.getById(id));
    }

    @PostMapping
    public ResponseEntity<PromoCode> create(@RequestBody PromoCode promoCode) {
        return ResponseEntity.status(201).body(promoCodeService.create(promoCode));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromoCode> update(@PathVariable Long id, @RequestBody PromoCode promoCode) {
        return ResponseEntity.ok(promoCodeService.update(id, promoCode));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promoCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
