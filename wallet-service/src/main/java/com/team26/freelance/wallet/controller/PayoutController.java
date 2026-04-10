package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.PayoutResponseDTO;
import com.team26.freelance.wallet.service.PayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payouts")
public class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @PostMapping("/{payoutId}/promos/{promoCodeId}")
    public ResponseEntity<PayoutResponseDTO> applyPromoCodeToPayout(
            @PathVariable Long payoutId,
            @PathVariable Long promoCodeId
    ) {
        return ResponseEntity.ok(payoutService.applyPromoToPayout(payoutId, promoCodeId));
    }
}
