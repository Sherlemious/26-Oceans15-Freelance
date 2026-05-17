package com.team26.freelance.security;

import com.team26.freelance.security.handler.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(SecurityFilterChain.class)
public class JwtSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(DataSource dataSource, Environment environment) {

        JwtConfigurationManager config = JwtConfigurationManager.getInstance();
        DataSource userValidationDataSource = resolveUserValidationDataSource(dataSource, environment);

        TokenExtractionHandler  h1 = new TokenExtractionHandler();
        TokenValidationHandler  h2 = new TokenValidationHandler(config);
        ClaimsExtractionHandler h3 = new ClaimsExtractionHandler();
        UserLoaderHandler       h4 = new UserLoaderHandler(userValidationDataSource);
        SecurityContextHandler  h5 = new SecurityContextHandler();

        h1.setNext(h2).setNext(h3).setNext(h4).setNext(h5);

        return new JwtAuthenticationFilter(h1);
    }

    private DataSource resolveUserValidationDataSource(DataSource applicationDataSource,
                                                       Environment environment) {
        String url = environment.getProperty("JWT_USER_DB_URL");
        if (url == null || url.isBlank()) {
            return applicationDataSource;
        }

        DriverManagerDataSource userValidationDataSource = new DriverManagerDataSource();
        userValidationDataSource.setDriverClassName(
                environment.getProperty("JWT_USER_DB_DRIVER_CLASS_NAME", "org.postgresql.Driver")
        );
        userValidationDataSource.setUrl(url);
        userValidationDataSource.setUsername(environment.getProperty("JWT_USER_DB_USERNAME", "postgres"));
        userValidationDataSource.setPassword(environment.getProperty("JWT_USER_DB_PASSWORD", "postgres"));
        return userValidationDataSource;
    }
}