package com.team26.freelance.wallet.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PayoutAnalyticsCacheService {

    private static final String METHOD_BREAKDOWN_PREFIX = "payout-analytics:method-breakdown:";
    private static final Duration METHOD_BREAKDOWN_TTL = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(PayoutAnalyticsCacheService.class);

    private final StringRedisTemplate redisTemplate;

    public PayoutAnalyticsCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static String cacheKey(LocalDate startDate, LocalDate endDate) {
        return METHOD_BREAKDOWN_PREFIX + startDate + ":" + endDate;
    }

    public String getMethodBreakdown(LocalDate startDate, LocalDate endDate) {
        try {
            return redisTemplate.opsForValue().get(cacheKey(startDate, endDate));
        } catch (RuntimeException ex) {
            log.warn("Failed to read payout method breakdown cache", ex);
            return null;
        }
    }

    public void putMethodBreakdown(LocalDate startDate, LocalDate endDate, String value) {
        try {
            redisTemplate.opsForValue().set(cacheKey(startDate, endDate), value, METHOD_BREAKDOWN_TTL);
        } catch (RuntimeException ex) {
            log.warn("Failed to write payout method breakdown cache", ex);
        }
    }

    public void evictMethodBreakdown() {
        try {
            Set<String> keys = redisTemplate.keys(METHOD_BREAKDOWN_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to evict payout method breakdown cache", ex);
        }
    }
}
