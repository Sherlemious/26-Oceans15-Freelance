package com.team26.freelance.job.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {

    @GetMapping("/api/contracts/job/{jobId}/active-count")
    int getActiveContractCountForJob(@PathVariable("jobId") Long jobId);

    @GetMapping("/api/contracts/{contractId}")
    ContractDTO getContract(@PathVariable("contractId") Long contractId);
}