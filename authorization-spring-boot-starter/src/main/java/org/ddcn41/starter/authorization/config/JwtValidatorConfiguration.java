package org.ddcn41.starter.authorization.config;

import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.ddcn41.starter.authorization.validator.CognitoJwtValidator;
import org.ddcn41.starter.authorization.validator.CognitoSigningKeyResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JwtValidatorConfiguration {

    @Bean
    public CognitoSigningKeyResolver cognitoSigningKeyResolver(JwtProperties jwtProperties) {
        return new CognitoSigningKeyResolver(jwtProperties);
    }

    @Bean
    public CognitoJwtValidator cognitoJwtValidator(
            JwtProperties jwtProperties,
            CognitoSigningKeyResolver signingKeyResolver) {

        return new CognitoJwtValidator(jwtProperties, signingKeyResolver);
    }
}
