package com.team26.freelance.proposal.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> specificConfigs = new HashMap<>();
        // Set exact TTLs per M2 Spec Section 4.4.1
        specificConfigs.put("proposal-service::S3-F1", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        specificConfigs.put("proposal-service::S3-F3", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        specificConfigs.put("proposal-service::S3-F5", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        specificConfigs.put("proposal-service::S3-F6", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        specificConfigs.put("proposal-service::S3-F9", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        specificConfigs.put("proposal-service::proposal", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(specificConfigs)
                .build();
    }
}