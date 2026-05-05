package com.team26.freelance.wallet.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PayoutAnalyticsCacheService {

    private static final String PAYOUT_DETAIL_CACHE = "wallet-service::payout";
    private static final String PLATFORM_FEE_ANALYTICS_CACHE = "wallet-service::S5-F10";
    private static final String METHOD_BREAKDOWN_CACHE = "wallet-service::S5-F11";
    private static final Logger log = LoggerFactory.getLogger(PayoutAnalyticsCacheService.class);

    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;

    public PayoutAnalyticsCacheService(CacheManager cacheManager,
                                       StringRedisTemplate redisTemplate) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    private static String cacheKey(LocalDate startDate, LocalDate endDate) {
        return startDate + ":" + endDate;
    }

    public String getMethodBreakdown(LocalDate startDate, LocalDate endDate) {
        try {
            Cache.ValueWrapper cached = getCache(METHOD_BREAKDOWN_CACHE)
                    .get(cacheKey(startDate, endDate));
            if (cached == null) {
                return null;
            }
            Object value = cached.get();
            if (value instanceof String cachedValue) {
                return cachedValue;
            }
            evictMethodBreakdown();
            return null;
        } catch (RuntimeException ex) {
            log.warn("Failed to read payout method breakdown cache", ex);
            return null;
        }
    }

    public void putMethodBreakdown(LocalDate startDate, LocalDate endDate, String value) {
        try {
            getCache(METHOD_BREAKDOWN_CACHE).put(cacheKey(startDate, endDate), value);
        } catch (RuntimeException ex) {
            log.warn("Failed to write payout method breakdown cache", ex);
        }
    }

    public void evictAnalyticsCaches() {
        evictCacheByPattern(
                PLATFORM_FEE_ANALYTICS_CACHE,
                PLATFORM_FEE_ANALYTICS_CACHE + "::*",
                "platform fee analytics"
        );
        evictMethodBreakdown();
    }

    public void evictMethodBreakdown() {
        evictCacheByPattern(
                METHOD_BREAKDOWN_CACHE,
                METHOD_BREAKDOWN_CACHE + "::*",
                "payout method breakdown"
        );
    }

    public void evictPayoutDetail(Long payoutId) {
        if (payoutId == null) {
            return;
        }
        String key = PAYOUT_DETAIL_CACHE + "::" + payoutId;
        try {
            redisTemplate.delete(key);
            getCache(PAYOUT_DETAIL_CACHE).evict(payoutId);
        } catch (RuntimeException ex) {
            log.warn("Failed to evict payout detail cache", ex);
        }
    }

    private Cache getCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + cacheName);
        }
        return cache;
    }

    private void evictCache(String cacheName, String description) {
        try {
            getCache(cacheName).clear();
        } catch (RuntimeException ex) {
            log.warn("Failed to evict {} cache", description, ex);
        }
    }

    private void evictCacheByPattern(String cacheName, String pattern, String description) {
        try {
            Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<String> matchingKeys = new HashSet<>();
                ScanOptions options = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    cursor.forEachRemaining(key ->
                            matchingKeys.add(new String(key, StandardCharsets.UTF_8))
                    );
                }
                return matchingKeys;
            });
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to wildcard-evict {} cache; falling back to Cache.clear()", description, ex);
            evictCache(cacheName, description);
        }
    }
}
