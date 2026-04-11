package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.service.ContractPurgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class HealthController {

    private final ContractPurgeService purgeService;

    public HealthController(ContractPurgeService purgeService) {
        this.purgeService = purgeService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @DeleteMapping("/purge")
    public ResponseEntity<Map<String, Long>> purge(@RequestParam int olderThanDays) {
        long deleted = purgeService.purgeOldContracts(olderThanDays);
        return ResponseEntity.ok(Map.of("deletedCount", deleted));
    }
}