package com.team26.freelance.wallet.controller;

import com.team26.freelance.wallet.dto.RevenueReportDTO;
import com.team26.freelance.wallet.service.PayoutReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payouts")
public class PayoutReportController {

    private final PayoutReportService payoutReportService;

    public PayoutReportController(PayoutReportService payoutReportService) {
        this.payoutReportService = payoutReportService;
    }

    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(payoutReportService.getRevenueReport(startDate, endDate));
    }
}
