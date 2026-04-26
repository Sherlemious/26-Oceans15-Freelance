package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import com.team26.freelance.wallet.service.PayoutAnalyticsService;
import com.team26.freelance.wallet.service.WalletJwtService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payouts/analytics")
public class PayoutAnalyticsController {

    private final PayoutAnalyticsService payoutAnalyticsService;
    private final WalletJwtService walletJwtService;

    public PayoutAnalyticsController(PayoutAnalyticsService payoutAnalyticsService,
                                     WalletJwtService walletJwtService) {
        this.payoutAnalyticsService = payoutAnalyticsService;
        this.walletJwtService = walletJwtService;
    }

    @GetMapping("/method-breakdown")
    public ResponseEntity<List<PayoutMethodBreakdownDTO>> getMethodBreakdown(
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        walletJwtService.validateAuthorizationHeader(authorization);
        return ResponseEntity.ok(payoutAnalyticsService.getMethodBreakdown());
    }
}
