package com.team26.freelance.contract.repository;

public interface ContractAnalyticsAggregateProjection {
    Long getTotalContracts();

    Double getAverageContractBudget();

    Long getCompletedContracts();

    Long getContractsCompletedLast30Days();
}
