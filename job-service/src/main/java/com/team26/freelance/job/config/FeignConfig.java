package com.team26.freelance.job.config;

import feign.Logger;
import feign.RequestInterceptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(FeignConfig.class);

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    Logger feignLogger() {
        return new Logger.JavaLogger(FeignConfig.class);
    }

    @Bean
    public RequestInterceptor feignRequestInterceptor() {
        return template -> {
            log.info("[Feign] {} {}", template.method(), template.url());
            log.info("[Feign-Headers] {}", template.headers());
        };
    }
}

