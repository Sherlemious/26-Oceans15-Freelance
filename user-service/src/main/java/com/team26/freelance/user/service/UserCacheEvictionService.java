package com.team26.freelance.user.service;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

@Service
public class UserCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(UserCacheEvictionService.class);
    private static final String USER_CACHE_PREFIX = "user-service::user::";
    private static final String ACTIVITY_FEED_CACHE_PATTERN = "user-service::S1-F12::*";

    private final StringRedisTemplate redisTemplate;

    public UserCacheEvictionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictUserDetail(Long userId) {
        try {
            redisTemplate.delete(USER_CACHE_PREFIX + userId);
        } catch (Exception ex) {
            log.warn("Failed to evict user detail cache for user {}", userId, ex);
        }
    }

    public void evictActivityFeedCaches() {
        try {
            evictByPattern(ACTIVITY_FEED_CACHE_PATTERN);
        } catch (Exception ex) {
            log.warn("Failed to evict activity feed caches", ex);
        }
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
