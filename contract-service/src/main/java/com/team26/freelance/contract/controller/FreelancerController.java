package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.dto.FreelancerPerformanceDTO;
import com.team26.freelance.contract.service.FreelancerPerformanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts/freelancer")
public class FreelancerController {

    private final FreelancerPerformanceService performanceService;

    public FreelancerController(FreelancerPerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @GetMapping("/{freelancerId}/summary")
    public ResponseEntity<FreelancerPerformanceDTO> getSummary(
            @PathVariable Long freelancerId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

        FreelancerPerformanceDTO summary = performanceService.getSummary(freelancerId, startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}

