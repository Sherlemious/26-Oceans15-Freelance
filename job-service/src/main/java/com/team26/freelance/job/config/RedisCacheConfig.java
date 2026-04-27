package com.team26.freelance.job.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

        ObjectMapper objectMapper = new ObjectMapper();

        // Handle LocalDateTime correctly
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Don't fail on unknown fields — safer for schema evolution
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Required: embeds @class type info so Jackson can reconstruct
        // the correct type (Job, DTO, etc.) instead of LinkedHashMap on read
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // CRUD detail caches — 15 min (§4.4.2)
        cacheConfigurations.put("job",                     defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("job-attachment",          defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // S2 Feature caches — TTL per spec §4.4
        cacheConfigurations.put("job-search",              defaultConfig.entryTtl(Duration.ofMinutes(5)));   // F1
        cacheConfigurations.put("job-proposal-summary",    defaultConfig.entryTtl(Duration.ofMinutes(10)));  // F3
        cacheConfigurations.put("job-requirements-search", defaultConfig.entryTtl(Duration.ofMinutes(5)));   // F5
        cacheConfigurations.put("top-budget-jobs",         defaultConfig.entryTtl(Duration.ofMinutes(10)));  // F6
        cacheConfigurations.put("job-expired-attachments", defaultConfig.entryTtl(Duration.ofMinutes(10)));  // F9

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    // Required by CacheEvictionService for SCAN + UNLINK
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    // Graceful degradation — Redis down = fall through to DB, not HTTP 500
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                System.err.println("[Cache] GET failed cache=" + cache.getName()
                        + " key=" + key + ": " + e.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException e, Cache cache,
                                            Object key, Object value) {
                System.err.println("[Cache] PUT failed cache=" + cache.getName()
                        + " key=" + key + ": " + e.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                System.err.println("[Cache] EVICT failed cache=" + cache.getName()
                        + " key=" + key + ": " + e.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                System.err.println("[Cache] CLEAR failed cache=" + cache.getName()
                        + ": " + e.getMessage());
            }
        };
    }
}