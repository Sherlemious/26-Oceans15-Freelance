package com.team26.freelance.user.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String USER_DETAIL_CACHE = "user-detail";
    public static final String USER_SKILL_DETAIL_CACHE = "user-skill-detail";
    public static final String S1_F1_CACHE = "s1-f1-user-search";
    public static final String S1_F3_CACHE = "s1-f3-contract-summary";
    public static final String S1_F5_CACHE = "s1-f5-preference-search";
    public static final String S1_F6_CACHE = "s1-f6-top-freelancers";
    public static final String S1_F8_CACHE = "s1-f8-user-profile";
    public static final String S1_F9_CACHE = "s1-f9-language-preferences";
    public static final String S1_F12_CACHE = "s1-f12-activity-feed";

    private final RedisConnectionFactory connectionFactory;

    public CacheConfig(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration defaultConfiguration = redisCacheConfiguration(Duration.ofMinutes(15));

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                USER_DETAIL_CACHE, redisCacheConfiguration(Duration.ofMinutes(15)),
                USER_SKILL_DETAIL_CACHE, redisCacheConfiguration(Duration.ofMinutes(15)),
                S1_F1_CACHE, redisCacheConfiguration(Duration.ofMinutes(5)),
                S1_F3_CACHE, redisCacheConfiguration(Duration.ofMinutes(10)),
                S1_F5_CACHE, redisCacheConfiguration(Duration.ofMinutes(5)),
                S1_F6_CACHE, redisCacheConfiguration(Duration.ofMinutes(10)),
                S1_F8_CACHE, redisCacheConfiguration(Duration.ofMinutes(15)),
                S1_F9_CACHE, redisCacheConfiguration(Duration.ofMinutes(10)),
                S1_F12_CACHE, redisCacheConfiguration(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private RedisCacheConfiguration redisCacheConfiguration(Duration ttl) {
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .build()
                .configure(mapper -> {
                    mapper.registerModule(new JavaTimeModule());
                    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                });

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .disableKeyPrefix()
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }

    private static class LoggingCacheErrorHandler implements CacheErrorHandler {

        private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.warn("Cache get failed for cache {} key {}", cache.getName(), key, exception);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.warn("Cache put failed for cache {} key {}", cache.getName(), key, exception);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.warn("Cache evict failed for cache {} key {}", cache.getName(), key, exception);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.warn("Cache clear failed for cache {}", cache.getName(), exception);
        }
    }
}
