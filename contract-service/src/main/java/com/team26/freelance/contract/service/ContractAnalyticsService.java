package com.team26.freelance.contract.service;

import com.team26.freelance.common.event.ObservabilityAction;
import com.team26.freelance.contract.adapter.MongoDocumentAdapter;
import com.team26.freelance.contract.config.ContractCacheKeys;
import com.team26.freelance.contract.dto.ContractAnalyticsDTO;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.observer.ContractEventSubject;
import com.team26.freelance.contract.repository.ContractAnalyticsProjection;
import com.team26.freelance.contract.repository.ContractRepository;
import com.team26.freelance.contract.repository.mongo.ContractEventRepository;
import org.bson.Document;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContractAnalyticsService {

    private static final LocalTime END_OF_DAY_MILLIS = LocalTime.of(23, 59, 59, 999_000_000);
    private static final String ANALYTICS_CACHE_NAME = "contract-s4-f10";
    private static final String ANALYTICS_FEATURE_ID = "S4-F10";

    private final ContractRepository contractRepository;
    private final ContractEventSubject contractEventSubject;
    private final MongoDocumentAdapter mongoDocumentAdapter;
    private final CacheManager cacheManager;
    private final ContractCacheKeys contractCacheKeys;

    public ContractAnalyticsService(ContractRepository contractRepository,
                                    ContractEventRepository contractEventRepository,
                                    ContractEventSubject contractEventSubject,
                                    ContractCacheEvictionService cacheEvictionService,
                                    MongoDocumentAdapter mongoDocumentAdapter,
                                    CacheManager cacheManager,
                                    ContractCacheKeys contractCacheKeys) {
        this.contractRepository = contractRepository;
        this.contractEventSubject = contractEventSubject;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.cacheManager = cacheManager;
        this.contractCacheKeys = contractCacheKeys;
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

    public ContractAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(END_OF_DAY_MILLIS);
        String cacheKey = contractCacheKeys.featureKey(ANALYTICS_FEATURE_ID, startDate, endDate);
        Map<String, String> currentSourceSignatures = getSourceSignatures(startDateTime, endDateTime);

        AnalyticsCacheEntry cachedEntry = getCachedEntry(cacheKey);
        if (isCachedEntryReusable(cachedEntry, currentSourceSignatures)) {
            return cachedEntry.getAnalytics();
        }

        ContractAnalyticsDTO analytics = calculateAnalytics(startDateTime, endDateTime);
        putCachedEntry(cacheKey, new AnalyticsCacheEntry(analytics, currentSourceSignatures));
        return analytics;
    }

    private ContractAnalyticsDTO calculateAnalytics(LocalDateTime startDateTime, LocalDateTime endDateTime) {
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

    private AnalyticsCacheEntry getCachedEntry(String cacheKey) {
        Cache cache = getAnalyticsCache();
        if (cache == null) {
            return null;
        }
        try {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof AnalyticsCacheEntry entry) {
                return entry;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private void putCachedEntry(String cacheKey, AnalyticsCacheEntry entry) {
        Cache cache = getAnalyticsCache();
        if (cache == null) {
            return;
        }
        try {
            cache.put(cacheKey, entry);
        } catch (RuntimeException ignored) {
            // Redis is a soft dependency for this endpoint.
        }
    }

    private Cache getAnalyticsCache() {
        try {
            return cacheManager.getCache(ANALYTICS_CACHE_NAME);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isCachedEntryReusable(AnalyticsCacheEntry cachedEntry, Map<String, String> currentSourceSignatures) {
        if (cachedEntry == null || cachedEntry.getAnalytics() == null || cachedEntry.getSourceSignatures() == null) {
            return false;
        }

        return cachedEntry.getSourceSignatures().entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(currentSourceSignatures.get(entry.getKey())));
    }

    private Map<String, String> getSourceSignatures(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Map<String, String> signatures = new LinkedHashMap<>();
        List<Object[]> rows = contractRepository.findContractAnalyticsSourceSignatures(startDateTime, endDateTime);
        for (Object[] row : rows) {
            if (row[0] != null) {
                signatures.put(String.valueOf(row[0]), sourceSignature(row));
            }
        }
        return signatures;
    }

    private String sourceSignature(Object[] row) {
        return canonicalValue(row[1]) + "|"
                + canonicalValue(row[2]) + "|"
                + canonicalValue(row[3]) + "|"
                + canonicalValue(row[4]);
    }

    private String canonicalValue(Object value) {
        return value == null ? "null" : String.valueOf(value);
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
            orderedCounts.put(status.name(), count);
        }
        return orderedCounts;
    }

    public static class AnalyticsCacheEntry {
        private ContractAnalyticsDTO analytics;
        private Map<String, String> sourceSignatures = new LinkedHashMap<>();

        public AnalyticsCacheEntry() {
        }

        public AnalyticsCacheEntry(ContractAnalyticsDTO analytics, Map<String, String> sourceSignatures) {
            this.analytics = analytics;
            this.sourceSignatures = sourceSignatures == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sourceSignatures);
        }

        public ContractAnalyticsDTO getAnalytics() {
            return analytics;
        }

        public void setAnalytics(ContractAnalyticsDTO analytics) {
            this.analytics = analytics;
        }

        public Map<String, String> getSourceSignatures() {
            return sourceSignatures;
        }

        public void setSourceSignatures(Map<String, String> sourceSignatures) {
            this.sourceSignatures = sourceSignatures == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sourceSignatures);
        }
    }
}
