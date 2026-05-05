package com.team26.freelance.user.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(UserCacheEvictionService.class);

    private final StringRedisTemplate redisTemplate;

    public UserCacheEvictionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictUserDetail(Long userId) {
        if (userId == null) {
            return;
        }
        evictExact(UserCacheKeys.user(userId));
    }

    public void evictUserMutationCaches(Long userId) {
        try {
            List<String> keys = new ArrayList<>();
            keys.add(UserCacheKeys.LEGACY_USERS_ALL);
            if (userId != null) {
                keys.add(UserCacheKeys.user(userId));
                keys.add(UserCacheKeys.legacyUser(userId));
            }
            deleteExact(keys);
            evictByPattern(UserCacheKeys.FEATURE_PATTERN);
        } catch (Exception ex) {
            log.warn("Failed to evict user mutation caches for user {}", userId, ex);
        }
    }

    public void evictUserSkillMutationCaches(Long skillId, Long userId) {
        try {
            List<String> keys = new ArrayList<>();
            if (skillId != null) {
                keys.add(UserCacheKeys.userSkill(skillId));
                keys.add(UserCacheKeys.legacyUserSkill(skillId));
            }
            if (userId != null) {
                keys.add(UserCacheKeys.user(userId));
            }
            deleteExact(keys);
            evictByPattern(UserCacheKeys.FEATURE_PATTERN);
        } catch (Exception ex) {
            log.warn("Failed to evict user-skill mutation caches for skill {} and user {}", skillId, userId, ex);
        }
    }

    public void evictActivityFeedCaches() {
        try {
            evictByPattern(UserCacheKeys.ACTIVITY_FEED_PATTERN);
        } catch (Exception ex) {
            log.warn("Failed to evict activity feed caches", ex);
        }
    }

    public void evictExact(String key) {
        if (key == null) {
            return;
        }
        try {
            deleteExact(List.of(key));
        } catch (Exception ex) {
            log.warn("Failed to evict cache key {}", key, ex);
        }
    }

    public void evictExact(Collection<String> keys) {
        try {
            deleteExact(keys);
        } catch (Exception ex) {
            log.warn("Failed to evict cache keys {}", keys, ex);
        }
    }

    private void deleteExact(Collection<String> keys) {
        List<String> safeKeys = keys == null ? List.of() : keys.stream()
                .filter(Objects::nonNull)
                .toList();
        if (safeKeys.isEmpty()) {
            return;
        }
        redisTemplate.delete(safeKeys);
    }

    private void evictByPattern(String pattern) {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(200)
                    .build();
            List<byte[]> keys = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (!keys.isEmpty()) {
                connection.keyCommands().unlink(keys.toArray(new byte[0][]));
            }
            return null;
        });
    }
}
