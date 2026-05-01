package com.team26.freelance.wallet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheErrorConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheErrorConfig.class);

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn(
                        "Cache GET failed for cache '{}' and key '{}'. Falling back to method execution. {}",
                        cacheName(cache), key, exception.getMessage()
                );
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn(
                        "Cache PUT failed for cache '{}' and key '{}'. Continuing without caching. {}",
                        cacheName(cache), key, exception.getMessage()
                );
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn(
                        "Cache EVICT failed for cache '{}' and key '{}'. Continuing without eviction. {}",
                        cacheName(cache), key, exception.getMessage()
                );
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn(
                        "Cache CLEAR failed for cache '{}'. Continuing. {}",
                        cacheName(cache), exception.getMessage()
                );
            }
        };
    }

    private String cacheName(Cache cache) {
        return cache != null ? cache.getName() : "unknown";
    }
}