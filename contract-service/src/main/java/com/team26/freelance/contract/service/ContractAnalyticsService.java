package com.team26.freelance.contract.service;

import com.team26.freelance.common.event.ObservabilityAction;
import com.team26.freelance.contract.adapter.MongoDocumentAdapter;
import com.team26.freelance.contract.dto.ContractAnalyticsDTO;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.observer.ContractEventSubject;
import com.team26.freelance.contract.repository.ContractAnalyticsProjection;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.repository.mongo.ContractEventRepository;
import org.bson.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContractAnalyticsService {

    private static final LocalTime END_OF_DAY_MILLIS = LocalTime.of(23, 59, 59, 999_000_000);

    private final ContractRepository contractRepository;
    private final ContractEventSubject contractEventSubject;
    private final MongoDocumentAdapter mongoDocumentAdapter;

    public ContractAnalyticsService(ContractRepository contractRepository,
                                    ContractEventRepository contractEventRepository,
                                    ContractEventSubject contractEventSubject,
                                    ContractCacheEvictionService cacheEvictionService,
                                    MongoDocumentAdapter mongoDocumentAdapter) {
        this.contractRepository = contractRepository;
        this.contractEventSubject = contractEventSubject;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
    }

    public void recordAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        contractEventSubject.notifyObservers(ObservabilityAction.ANALYTICS_VIEWED.name(), Map.of(
            "details", Map.of(
                "startDate", startDate.toString(),
                "endDate", endDate.toString(),
                "source", "contract-analytics"
            )
        ));
    }

    @Cacheable(value = "contract-s4-f10", key = "@contractCacheKeys.featureKey('S4-F10', #startDate, #endDate)")
    public ContractAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(END_OF_DAY_MILLIS);
        ContractAnalyticsProjection summary = contractRepository.getContractAnalytics(startDateTime, endDateTime);
        Map<String, Long> contractsByStatus = getContractsByStatus(startDateTime, endDateTime);

        Document source = new Document()
                .append("totalContracts", summary == null ? 0L : summary.getTotalContracts())
                .append("averageContractValue", summary == null ? 0.0 : summary.getAverageContractValue())
                .append("completionRate", summary == null ? 0.0 : summary.getCompletionRate())
                .append("averageContractDurationDays",
                        summary == null ? 0.0 : summary.getAverageContractDurationDays())
                .append("contractsByStatus", contractsByStatus);

        return mongoDocumentAdapter.adapt(source);
    }

    private Map<String, Long> getContractsByStatus(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Map<String, Long> rawCounts = new LinkedHashMap<>();
        for (Object[] row : contractRepository.countContractsByStatus(startDateTime, endDateTime)) {
            if (row[0] != null) {
                rawCounts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        Map<String, Long> orderedCounts = new LinkedHashMap<>();
        for (ContractStatus status : ContractStatus.values()) {
            long count = rawCounts.getOrDefault(status.name(), 0L);
            if (count > 0) {
                orderedCounts.put(status.name(), count);
            }
        }
        return orderedCounts;
    }
}
