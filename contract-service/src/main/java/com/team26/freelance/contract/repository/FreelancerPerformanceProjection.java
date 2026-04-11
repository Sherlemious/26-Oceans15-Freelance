package com.team26.freelance.contract.repository;

public interface FreelancerPerformanceProjection {
    Long getTotalContracts();
    Double getAverageContractValue();
    Double getTotalEarnings();
    Double getCompletionRate();
    Double getAverageDurationDays();
}

