package com.team26.freelance.contract.service;

import com.team26.freelance.common.ObservabilityAction;
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
import java.util.Objects;

@Service
public class ContractCacheEvictionService {

    private static final Logger logger = LoggerFactory.getLogger(ContractCacheEvictionService.class);
    private static final String PREFIX = "contract-service::";

    private final RedisTemplate<String, Object> redisTemplate;

    public ContractCacheEvictionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictContractDetail(Long id) {
        if (id == null) {
            return;
        }
        String key = PREFIX + "contract::" + id;
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            logger.warn("Redis exact eviction failed for key={}; continuing without cache eviction",
                    key, e);
        }
    }

    public void evictContractDetails(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<String> keys = ids.stream()
                .filter(Objects::nonNull)
                .map(id -> PREFIX + "contract::" + id)
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (RuntimeException e) {
            logger.warn("Redis batch exact eviction failed for keys={}; continuing without cache eviction",
                    keys, e);
        }
    }

    public void evictAllContractFeatureCaches() {
        evictByPattern(PREFIX + "S4-F*::*");
    }

    public void evictContractAnalyticsDashboard() {
        evictByPattern(PREFIX + "S4-F10::*");
    }

    public void evictMilestoneTimeline(Long contractId) {
        if (contractId == null) {
            return;
        }
        evictByPattern(PREFIX + "S4-F12::" + contractId + "*");
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
        return ObservabilityAction.isPureObservabilityAction(action);
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
