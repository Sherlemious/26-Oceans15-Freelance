package com.team26.freelance.contract.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
public class ContractCacheEvictionService {

    private static final Logger logger = LoggerFactory.getLogger(ContractCacheEvictionService.class);
    private static final String PREFIX = "contract-service::";
    private static final String ANALYTICS_VIEWED = "ANALYTICS_VIEWED";
    private static final String DASHBOARD_VIEWED = "DASHBOARD_VIEWED";

    private final RedisTemplate<String, Object> redisTemplate;

    public ContractCacheEvictionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictContractDetail(Long id) {
        evictByPattern(PREFIX + "contract::" + id);
    }

    public void evictContractDetails(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(this::evictContractDetail);
    }

    public void evictAllContractFeatureCaches() {
        evictByPattern(PREFIX + "S4-F*::*");
    }

    public void evictContractAnalyticsDashboard() {
        evictByPattern(PREFIX + "S4-F10::*");
    }

    public void evictMilestoneTimeline(Long contractId) {
        evictByPattern(PREFIX + "S4-F12::" + contractId);
    }

    public void evictAfterContractCreated() {
        evictAllContractFeatureCaches();
        evictContractAnalyticsDashboard();
    }

    public void evictAfterContractMutation(Long id) {
        evictContractDetail(id);
        evictAllContractFeatureCaches();
        evictContractAnalyticsDashboard();
    }

    public void evictAfterContractsMutated(Collection<Long> ids) {
        evictContractDetails(ids);
        evictAllContractFeatureCaches();
        evictContractAnalyticsDashboard();
    }

    public void evictAfterMilestoneTracked(Long contractId) {
        evictMilestoneTimeline(contractId);
        evictContractAnalyticsDashboard();
    }

    public void evictAnalyticsForObserverEvent(String action) {
        if (!isPureObservabilityAction(action)) {
            evictContractAnalyticsDashboard();
        }
    }

    public boolean isPureObservabilityAction(String action) {
        if (action == null) {
            return false;
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        return ANALYTICS_VIEWED.equals(normalized) || DASHBOARD_VIEWED.equals(normalized);
    }

    public void evictByPattern(String pattern) {
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(500)
                        .build();
                List<byte[]> keys = new ArrayList<>();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                        if (keys.size() >= 500) {
                            unlink(connection, keys);
                            keys.clear();
                        }
                    }
                }
                unlink(connection, keys);
                return null;
            });
        } catch (RuntimeException e) {
            logger.warn("Redis wildcard eviction failed for pattern={}; continuing without cache eviction",
                    pattern, e);
        }
    }

    private void unlink(RedisConnection connection, List<byte[]> keys) {
        if (!keys.isEmpty()) {
            connection.keyCommands().unlink(keys.toArray(new byte[0][]));
        }
    }
}
