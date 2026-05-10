package com.team26.freelance.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "wallet-service", url = "${feign.wallet-service.url}")
public interface WalletServiceClient {

    @GetMapping("/api/payouts/freelancer/{freelancerId}/total")
    BigDecimal getFreelancerPayoutTotal(
            @PathVariable Long freelancerId,
            @RequestParam String startDate,
            @RequestParam String endDate
    );
}
