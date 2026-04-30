package com.team26.freelance.security;

import com.team26.freelance.security.handler.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration
@ConditionalOnClass(SecurityFilterChain.class)
public class JwtSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtConfigurationManager jwtConfigurationManager() {
        return JwtConfigurationManager.getInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtConfigurationManager config) {

        TokenExtractionHandler  h1 = new TokenExtractionHandler();
        TokenValidationHandler  h2 = new TokenValidationHandler(config);
        ClaimsExtractionHandler h3 = new ClaimsExtractionHandler();
        SecurityContextHandler  h4 = new SecurityContextHandler();

        h1.setNext(h2).setNext(h3).setNext(h4);

        return new JwtAuthenticationFilter(h1);
    }
}