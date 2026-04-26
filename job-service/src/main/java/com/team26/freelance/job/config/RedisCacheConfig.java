package com.team26.freelance.job.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 1. Configure ObjectMapper to handle Java 8 Dates (LocalDateTime)
        ObjectMapper objectMapper = new ObjectMapper();

        // Register the module that handles java.time types
        objectMapper.registerModule(new JavaTimeModule());

        // IMPORTANT: This allows Spring to re-construct the actual Class (e.g., Job or DTO)
        // from JSON. Without this, it might try to turn everything into a LinkedHashMap.
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // 2. Create the Serializer with our custom mapper
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // 3. Define the default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // Default TTL as per section 4.4
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // 4. Define specific configurations for your Milestone 2 features
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Feature: Job Details (CRUD GET /api/jobs/{id})
        cacheConfigurations.put("job", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Feature: Job Attachments
        cacheConfigurations.put("job-attachments", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Feature: Proposal Summary Report
        cacheConfigurations.put("job-proposal-summary", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Feature: Top Budget Jobs (Reports)
        cacheConfigurations.put("top-budget-jobs", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 5. Build the CacheManager
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}