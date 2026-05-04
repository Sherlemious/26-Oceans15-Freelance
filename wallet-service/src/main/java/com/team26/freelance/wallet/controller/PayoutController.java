package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.FreelancerPayoutSummaryDTO;
import com.team26.freelance.wallet.dto.PayoutDetailsDTO;
import com.team26.freelance.wallet.dto.PayoutResponseDTO;
import com.team26.freelance.wallet.dto.PayoutReversalResultDTO;
import com.team26.freelance.wallet.dto.ProcessContractPayoutRequest;
import com.team26.freelance.wallet.dto.PromoCodeUsageDTO;
import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.dto.CategoryRevenueDTO;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.service.PayoutService;
import com.team26.freelance.wallet.service.PlatformFeeAnalyticsService;
import java.time.LocalDate;
import java.util.List;
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


@RestController
@RequestMapping("/api/payouts")
public class PayoutController {

  private final PayoutService payoutService;
  private final PlatformFeeAnalyticsService platformFeeAnalyticsService;

  public PayoutController(PayoutService payoutService,
                          PlatformFeeAnalyticsService platformFeeAnalyticsService) {
    this.payoutService = payoutService;
    this.platformFeeAnalyticsService = platformFeeAnalyticsService;
  }

  @GetMapping
  public ResponseEntity<List<Payout>> getAllPayouts() {
    return ResponseEntity.ok(payoutService.getAllPayouts());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Payout> getPayoutById(@PathVariable Long id) {
    return ResponseEntity.ok(payoutService.getPayoutById(id));
  }

  @GetMapping("/analytics/category")
  public ResponseEntity<List<CategoryRevenueDTO>> getPlatformFeeAnalyticsByCategory(
          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(
            platformFeeAnalyticsService.getPlatformFeeAnalytics(startDate, endDate)
    );
  }

  @PostMapping
  public ResponseEntity<Payout> createPayout(@RequestBody Payout payout) {
    return ResponseEntity.status(201).body(payoutService.createPayout(payout));
  }

  @PostMapping("/contract/{contractId}")
  public ResponseEntity<Payout>
  processContractPayout(@PathVariable Long contractId,
                        @RequestBody(required = false) ProcessContractPayoutRequest request,
                        @RequestParam(defaultValue = "false") boolean simulateFailure) {
    return ResponseEntity.status(201).body(
        payoutService.processContractPayout(contractId, request, simulateFailure));
  }

  @PutMapping("/{id}")
  public ResponseEntity<Payout> updatePayout(@PathVariable Long id,
                                             @RequestBody Payout payout) {
    return ResponseEntity.ok(payoutService.updatePayout(id, payout));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePayout(@PathVariable Long id) {
    payoutService.deletePayout(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/search")
  public ResponseEntity<List<Payout>>
  searchPayouts(@RequestParam(required = false) String status,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate startDate,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate endDate) {
    return ResponseEntity.ok(
        payoutService.searchByStatusAndDateRange(status, startDate, endDate));
  }

  @PutMapping("/{id}/refund")
  public ResponseEntity<Payout>
  refundPayout(@PathVariable Long id, @RequestBody RefundRequest request) {
    return ResponseEntity.ok(
        payoutService.processRefund(id, request.getReason()));
  }

  @PutMapping("/{id}/retry")
  public ResponseEntity<Payout> retryFailedPayout(@PathVariable Long id) {
    return ResponseEntity.ok(payoutService.retryFailedPayout(id));
  }

  @PostMapping("/{id}/reverse-milestone")
  public ResponseEntity<PayoutReversalResultDTO> reversePayout(@PathVariable Long id,
                                                               @RequestBody RefundRequest request) {
    return ResponseEntity.ok(payoutService.reversePayout(id, request));
  }

  @GetMapping("/{payoutId}/details")
  public ResponseEntity<PayoutDetailsDTO>
  getPayoutDetails(@PathVariable Long payoutId) {
    return ResponseEntity.ok(payoutService.getPayoutDetails(payoutId));
  }

  @GetMapping("/promos/top-used")
  public ResponseEntity<List<PromoCodeUsageDTO>>
  getTopUsedPromoCodes(@RequestParam int limit) {
    return ResponseEntity.ok(payoutService.getTopUsedPromoCodes(limit));
  }

  @PostMapping("/{payoutId}/promos/{promoCodeId}")
  public ResponseEntity<PayoutResponseDTO>
  applyPromoCodeToPayout(@PathVariable Long payoutId,
                         @PathVariable Long promoCodeId) {
    return ResponseEntity.ok(
        payoutService.applyPromoToPayout(payoutId, promoCodeId));
  }

  @GetMapping("/freelancer/{freelancerId}/summary")
  public ResponseEntity<FreelancerPayoutSummaryDTO> getFreelancerSummary(
      @PathVariable Long freelancerId) {
    return ResponseEntity.ok(payoutService.getFreelancerPayoutSummary(freelancerId));
  }
}
