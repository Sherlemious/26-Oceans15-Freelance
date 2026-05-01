package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.CreatePayoutPromoRequest;
import com.team26.freelance.wallet.dto.PayoutPromoDTO;
import com.team26.freelance.wallet.service.PayoutPromoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payout-promos")
public class PayoutPromoController {

    private final PayoutPromoService payoutPromoService;

    public PayoutPromoController(PayoutPromoService payoutPromoService) {
        this.payoutPromoService = payoutPromoService;
    }

    @PostMapping
    public ResponseEntity<PayoutPromoDTO> create(@RequestBody CreatePayoutPromoRequest request) {
        return ResponseEntity.status(201).body(payoutPromoService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayoutPromoDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(payoutPromoService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        payoutPromoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
