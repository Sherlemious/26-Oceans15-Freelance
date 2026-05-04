package com.team26.freelance.wallet.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisSerializationContext.SerializationPair<Object> jsonSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()
                );

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(jsonSerializer)
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put("wallet-service::payout",
                defaultConfig.entryTtl(Duration.ofMinutes(15)));

        cacheConfigs.put("wallet-service::S5-F1",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        cacheConfigs.put("wallet-service::S5-F3",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("wallet-service::S5-F6",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("wallet-service::S5-F8",
                defaultConfig.entryTtl(Duration.ofMinutes(15)));

        cacheConfigs.put("wallet-service::S5-F9",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("wallet-service::S5-F10",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}