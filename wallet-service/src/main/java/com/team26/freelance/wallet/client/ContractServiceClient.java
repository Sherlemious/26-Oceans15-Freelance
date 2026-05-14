package com.team26.freelance.wallet.client;

import com.team26.freelance.wallet.client.dto.ContractDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {

    @GetMapping("/api/contracts/{contractId}")
    ContractDTO getContract(@PathVariable Long contractId);
}
