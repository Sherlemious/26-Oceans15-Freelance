package com.team26.freelance.contract.repository;

public interface ContractAnalyticsProjection {
    Long getTotalContracts();
    Double getAverageContractValue();
    Double getCompletionRate();
    Double getAverageContractDurationDays();
}
