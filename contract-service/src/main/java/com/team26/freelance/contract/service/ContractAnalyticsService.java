package com.team26.freelance.contract.service;

import com.team26.freelance.contract.dto.ContractAnalyticsDashboardDTO;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.observer.ContractEventSubject;
import com.team26.freelance.contract.repository.ContractAnalyticsAggregateProjection;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.repository.ContractStatusCountProjection;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ContractAnalyticsService {
    private static final String ANALYTICS_VIEWED = "ANALYTICS_VIEWED";
    private static final String DASHBOARD_ENDPOINT = "/api/contracts/analytics/dashboard";

    private final ContractRepository contractRepository;
    private final ContractAnalyticsCacheService contractAnalyticsCacheService;
    private final ContractEventSubject contractEventSubject;

    public ContractAnalyticsService(ContractRepository contractRepository,
            ContractAnalyticsCacheService contractAnalyticsCacheService,
            ContractEventSubject contractEventSubject) {
        this.contractRepository = contractRepository;
        this.contractAnalyticsCacheService = contractAnalyticsCacheService;
        this.contractEventSubject = contractEventSubject;
    }

    public ContractAnalyticsDashboardDTO getDashboard() {
        Optional<ContractAnalyticsDashboardDTO> cachedDashboard = contractAnalyticsCacheService.getDashboard();
        if (cachedDashboard.isPresent()) {
            notifyAnalyticsViewed(true);
            return cachedDashboard.get();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        ContractAnalyticsAggregateProjection aggregate = contractRepository.getContractAnalyticsAggregate(cutoff);
        Map<ContractStatus, Long> contractsByStatus = getContractsByStatus();

        long totalContracts = valueOrZero(aggregate.getTotalContracts());
        long completedContracts = valueOrZero(aggregate.getCompletedContracts());
        double completionRate = totalContracts == 0 ? 0.0 : (double) completedContracts / totalContracts;

        ContractAnalyticsDashboardDTO dashboard = ContractAnalyticsDashboardDTO.builder()
                .totalContracts(totalContracts)
                .contractsByStatus(contractsByStatus)
                .averageContractBudget(aggregate.getAverageContractBudget() != null
                        ? aggregate.getAverageContractBudget()
                        : 0.0)
                .completionRate(completionRate)
                .contractsCompletedLast30Days(valueOrZero(aggregate.getContractsCompletedLast30Days()))
                .build();

        contractAnalyticsCacheService.putDashboard(dashboard);
        notifyAnalyticsViewed(false);
        return dashboard;
    }

    private Map<ContractStatus, Long> getContractsByStatus() {
        Map<ContractStatus, Long> contractsByStatus = new EnumMap<>(ContractStatus.class);
        for (ContractStatus status : ContractStatus.values()) {
            contractsByStatus.put(status, 0L);
        }
        for (ContractStatusCountProjection row : contractRepository.countContractsByStatus()) {
            ContractStatus status = ContractStatus.valueOf(row.getStatus());
            contractsByStatus.put(status, valueOrZero(row.getContractCount()));
        }
        return contractsByStatus;
    }

    private void notifyAnalyticsViewed(boolean cacheHit) {
        Map<String, Object> details = new HashMap<>();
        details.put("cacheHit", cacheHit);
        details.put("endpoint", DASHBOARD_ENDPOINT);
        contractEventSubject.notifyObservers(null, ANALYTICS_VIEWED, details);
    }

    private long valueOrZero(Long value) {
        return value != null ? value : 0L;
    }
}
