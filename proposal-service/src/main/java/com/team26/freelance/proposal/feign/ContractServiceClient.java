package com.team26.freelance.proposal.feign;

import com.team26.freelance.contracts.dto.ContractDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {

    @GetMapping("/api/contracts/{contractId}")
    ContractDTO getContract(@PathVariable("contractId") Long contractId);

    @GetMapping("/api/contracts/proposal/{proposalId}/active")
    ContractDTO getActiveContractForProposal(@PathVariable("proposalId") Long proposalId);
}
