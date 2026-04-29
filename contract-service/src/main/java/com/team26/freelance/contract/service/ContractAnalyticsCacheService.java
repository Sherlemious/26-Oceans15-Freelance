package com.team26.freelance.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contract.dto.ContractAnalyticsDashboardDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class ContractAnalyticsCacheService {
    public static final String DASHBOARD_KEY = "contract-analytics:dashboard";
    private static final Duration DASHBOARD_TTL = Duration.ofMinutes(10);
    private static final Logger logger = LoggerFactory.getLogger(ContractAnalyticsCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public ContractAnalyticsCacheService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ContractAnalyticsDashboardDTO> getDashboard() {
        try {
            Object cached = redisTemplate.opsForValue().get(DASHBOARD_KEY);
            if (cached == null) {
                return Optional.empty();
            }
            if (cached instanceof ContractAnalyticsDashboardDTO dashboard) {
                return Optional.of(dashboard);
            }
            return Optional.of(objectMapper.convertValue(cached, ContractAnalyticsDashboardDTO.class));
        } catch (RuntimeException e) {
            logger.warn("Unable to read contract analytics dashboard cache", e);
            return Optional.empty();
        }
    }

    public void putDashboard(ContractAnalyticsDashboardDTO dashboard) {
        try {
            redisTemplate.opsForValue().set(DASHBOARD_KEY, dashboard, DASHBOARD_TTL);
        } catch (RuntimeException e) {
            logger.warn("Unable to write contract analytics dashboard cache", e);
        }
    }

    public void evictDashboard() {
        try {
            redisTemplate.delete(DASHBOARD_KEY);
        } catch (RuntimeException e) {
            logger.warn("Unable to evict contract analytics dashboard cache", e);
        }
    }
}
