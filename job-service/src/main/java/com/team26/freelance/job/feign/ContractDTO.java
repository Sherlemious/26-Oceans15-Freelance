package com.team26.freelance.job.feign;

public record ContractDTO(
        Long id,
        Long jobId,
        Long freelancerId,
        Long clientId,
        Long proposalId,
        Double agreedAmount,
        String status) {}