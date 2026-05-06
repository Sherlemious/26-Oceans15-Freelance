package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import com.team26.freelance.wallet.service.PayoutAnalyticsService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payouts/analytics")
public class PayoutAnalyticsController {

    private final PayoutAnalyticsService payoutAnalyticsService;

    public PayoutAnalyticsController(PayoutAnalyticsService payoutAnalyticsService) {
        this.payoutAnalyticsService = payoutAnalyticsService;
    }

    @GetMapping("/methods")
    public ResponseEntity<List<PayoutMethodBreakdownDTO>> getMethodBreakdown(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(payoutAnalyticsService.getMethodBreakdown(startDate, endDate));
    }
}