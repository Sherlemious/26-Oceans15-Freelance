package com.team26.freelance.user.client;

import com.team26.freelance.user.dto.UserContractSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {

    @GetMapping("/api/contracts/user/{userId}/summary")
    UserContractSummaryDTO getUserContractSummary(@PathVariable Long userId);

    @GetMapping("/api/contracts/user/{userId}/active-count")
    int getActiveContractCount(@PathVariable Long userId);

    @GetMapping("/api/contracts/user/{userId}/completed-count")
    long getCompletedContractCount(@PathVariable Long userId);
}
