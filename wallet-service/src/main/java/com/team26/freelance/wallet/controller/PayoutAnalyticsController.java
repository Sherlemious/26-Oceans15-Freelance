package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import com.team26.freelance.wallet.service.PayoutAnalyticsService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payouts/analytics")
public class PayoutAnalyticsController {

    private final PayoutAnalyticsService payoutAnalyticsService;

    public PayoutAnalyticsController(PayoutAnalyticsService payoutAnalyticsService) {
        this.payoutAnalyticsService = payoutAnalyticsService;
    }

    @GetMapping("/method-breakdown")
    public ResponseEntity<List<PayoutMethodBreakdownDTO>> getMethodBreakdown(
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        return ResponseEntity.ok(payoutAnalyticsService.getMethodBreakdown());
    }
}
