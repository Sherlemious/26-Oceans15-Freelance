package com.team26.freelance.job.controller;

import com.team26.freelance.contracts.feign.ContractServiceClient;
import com.team26.freelance.contracts.feign.ProposalServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke test controller to verify Feign clients can be injected from shared contracts module.
 * Validates that OpenFeign setup in job-service can discover and inject clients.
 */
@RestController
@RequestMapping("/api/health/smoke-test")
public class FeignSmokeTestController {

    private final ContractServiceClient contractServiceClient;
    private final ProposalServiceClient proposalServiceClient;

    public FeignSmokeTestController(
            ContractServiceClient contractServiceClient,
            ProposalServiceClient proposalServiceClient) {
        this.contractServiceClient = contractServiceClient;
        this.proposalServiceClient = proposalServiceClient;
    }

    /**
     * Smoke test endpoint to verify Feign clients are properly injected.
     * Returns client information without calling remote endpoints.
     */
    @GetMapping("/feign-clients")
    public ResponseEntity<?> smokeTestFeignClients() {
        return ResponseEntity.ok().body(new SmokeTestResponse(
                "Feign clients successfully injected",
                contractServiceClient != null ? "ContractServiceClient ready" : "ContractServiceClient NOT found",
                proposalServiceClient != null ? "ProposalServiceClient ready" : "ProposalServiceClient NOT found"));
    }

    /**
     * Test endpoint to verify ContractServiceClient Feign interface is available.
     * Does NOT call remote endpoint to avoid dependency on contract-service availability.
     */
    @GetMapping("/contract-client-status")
    public ResponseEntity<?> getContractClientStatus() {
        String status = contractServiceClient != null ? "AVAILABLE" : "NOT_AVAILABLE";
        String className = contractServiceClient != null ? contractServiceClient.getClass().getName() : "NOT_INSTANTIATED";
        return ResponseEntity.ok().body(new ClientStatusResponse(
                "ContractServiceClient",
                status,
                className));
    }

    /**
     * Test endpoint to verify ProposalServiceClient Feign interface is available.
     * Does NOT call remote endpoint to avoid dependency on proposal-service availability.
     */
    @GetMapping("/proposal-client-status")
    public ResponseEntity<?> getProposalClientStatus() {
        String status = proposalServiceClient != null ? "AVAILABLE" : "NOT_AVAILABLE";
        String className = proposalServiceClient != null ? proposalServiceClient.getClass().getName() : "NOT_INSTANTIATED";
        return ResponseEntity.ok().body(new ClientStatusResponse(
                "ProposalServiceClient",
                status,
                className));
    }

    // DTOs for response
    record SmokeTestResponse(
            String message,
            String contractClientStatus,
            String proposalClientStatus) {}

    record ClientStatusResponse(
            String clientName,
            String status,
            String className) {}
}

