package com.team26.freelance.wallet.service;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class PayoutAnalyticsCacheService {

    private static final String PLATFORM_FEE_ANALYTICS_CACHE = "wallet-service::S5-F10";
    private static final String METHOD_BREAKDOWN_CACHE = "wallet-service::S5-F11";
    private static final Logger log = LoggerFactory.getLogger(PayoutAnalyticsCacheService.class);

    private final CacheManager cacheManager;

    public PayoutAnalyticsCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
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
        evictCache(PLATFORM_FEE_ANALYTICS_CACHE, "platform fee analytics");
        evictMethodBreakdown();
    }

    public void evictMethodBreakdown() {
        evictCache(METHOD_BREAKDOWN_CACHE, "payout method breakdown");
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
}
