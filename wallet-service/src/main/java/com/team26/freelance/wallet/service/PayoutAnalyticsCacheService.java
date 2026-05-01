package com.team26.freelance.wallet.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PayoutAnalyticsCacheService {

    public static final String METHOD_BREAKDOWN_KEY = "payout-analytics:method-breakdown";
    private static final Duration METHOD_BREAKDOWN_TTL = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(PayoutAnalyticsCacheService.class);

    private final StringRedisTemplate redisTemplate;

    public PayoutAnalyticsCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getMethodBreakdown() {
        try {
            return redisTemplate.opsForValue().get(METHOD_BREAKDOWN_KEY);
        } catch (RuntimeException ex) {
            log.warn("Failed to read payout method breakdown cache", ex);
            return null;
        }
    }

    public void putMethodBreakdown(String value) {
        try {
            redisTemplate.opsForValue().set(METHOD_BREAKDOWN_KEY, value, METHOD_BREAKDOWN_TTL);
        } catch (RuntimeException ex) {
            log.warn("Failed to write payout method breakdown cache", ex);
        }
    }

    public void evictMethodBreakdown() {
        try {
            redisTemplate.delete(METHOD_BREAKDOWN_KEY);
        } catch (RuntimeException ex) {
            log.warn("Failed to evict payout method breakdown cache", ex);
        }
    }
}
