package com.team26.freelance.user.config;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CacheEvictionService {

    private static final String PREFIX = "user-service::";
    private final RedisTemplate<String, Object> redisTemplate;

    public CacheEvictionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictUserActivityFeed(Long userId) {
        evictByPattern(PREFIX + "S1-F12::" + userId + "::*");
        evictByPattern(PREFIX + "S1-F12::" + userId + ":*");
    }

    public void evictByPattern(String pattern) {
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
                List<byte[]> toDelete = new ArrayList<>();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        toDelete.add(cursor.next());
                    }
                }
                if (!toDelete.isEmpty()) {
                    connection.keyCommands().unlink(toDelete.toArray(new byte[0][]));
                }
                return null;
            });
        } catch (Exception e) {
            System.err.println("[CacheEviction] Failed for pattern '" + pattern + "': " + e.getMessage());
        }
    }
}