package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.PayoutDetailsDTO;
import com.team26.freelance.wallet.dto.PayoutResponseDTO;
import com.team26.freelance.wallet.dto.PromoCodeUsageDTO;
import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.service.PayoutService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/payouts")
public class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping
    public ResponseEntity<List<Payout>> getAllPayouts() {
        return ResponseEntity.ok(payoutService.getAllPayouts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payout> getPayoutById(@PathVariable Long id) {
        return ResponseEntity.ok(payoutService.getPayoutById(id));
    }

    @PostMapping
    public ResponseEntity<Payout> createPayout(@RequestBody Payout payout) {
        return ResponseEntity.status(201).body(payoutService.createPayout(payout));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Payout> updatePayout(@PathVariable Long id, @RequestBody Payout payout) {
        return ResponseEntity.ok(payoutService.updatePayout(id, payout));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayout(@PathVariable Long id) {
        payoutService.deletePayout(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Payout>> searchPayouts(
            @RequestParam(required = false) String status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(payoutService.searchByStatusAndDateRange(status, startDate, endDate));
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Payout> refundPayout(@PathVariable Long id, @RequestBody RefundRequest request) {
        return ResponseEntity.ok(payoutService.processRefund(id, request.getReason()));
    }

    @PutMapping("/{id}/retry")
    public ResponseEntity<Payout> retryFailedPayout(@PathVariable Long id) {
        return ResponseEntity.ok(payoutService.retryFailedPayout(id));
    @GetMapping("/{payoutId}/details")
    public ResponseEntity<PayoutDetailsDTO> getPayoutDetails(@PathVariable Long payoutId) {
        return ResponseEntity.ok(payoutService.getPayoutDetails(payoutId));
    }

    @GetMapping("/promos/top-used")
    public ResponseEntity<List<PromoCodeUsageDTO>> getTopUsedPromoCodes(@RequestParam int limit) {
        return ResponseEntity.ok(payoutService.getTopUsedPromoCodes(limit));
    }

    @PostMapping("/{payoutId}/promos/{promoCodeId}")
    public ResponseEntity<PayoutResponseDTO> applyPromoCodeToPayout(
            @PathVariable Long payoutId,
            @PathVariable Long promoCodeId
    ) {
        return ResponseEntity.ok(payoutService.applyPromoToPayout(payoutId, promoCodeId));
    }
}
