package com.team26.freelance.proposal.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class ProposalCacheEvictionService {

    private final StringRedisTemplate redisTemplate;

    public ProposalCacheEvictionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictProposalCaches(Long proposalId) {
        // 1. Delete the specific detail cache (if an ID is provided)
        if (proposalId != null) {
            redisTemplate.delete("proposal-service::proposal::" + proposalId);
        }

        // 2. Wildcard deletion for all feature-result caches per M2 Spec 4.4.6
        Set<String> keysToDelete = redisTemplate.keys("proposal-service::S3-*");
        if (keysToDelete != null && !keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }

    public void evictS3F12Recommendations() {
        try {
            Set<String> keys = redisTemplate.keys("proposal-service::S3-F12::*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // Redis soft dependency
        }
    }
}