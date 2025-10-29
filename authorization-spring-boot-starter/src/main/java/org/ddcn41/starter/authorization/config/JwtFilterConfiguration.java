package org.ddcn41.starter.authorization.config;

import org.ddcn41.starter.authorization.filter.JwtAuthenticationFilter;
import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.ddcn41.starter.authorization.service.TokenBlacklistService;
import org.ddcn41.starter.authorization.validator.CognitoJwtValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@ConditionalOnProperty(prefix = "jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JwtFilterConfiguration {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtProperties jwtProperties,
            CognitoJwtValidator jwtValidator,
            Optional<TokenBlacklistService> blacklistService) {

        return new JwtAuthenticationFilter(jwtProperties, jwtValidator, blacklistService);
    }

}
