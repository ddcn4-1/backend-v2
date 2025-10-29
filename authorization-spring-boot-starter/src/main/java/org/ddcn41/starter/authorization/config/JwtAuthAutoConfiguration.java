package org.ddcn41.starter.authorization.config;


import io.jsonwebtoken.Jwts;
import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass({Jwts.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JwtProperties.class)
@Import({
        JwtValidatorConfiguration.class,
        JwtBlacklistConfiguration.class,
        JwtFilterConfiguration.class,
        JwtSecurityConfiguration.class
})
public class JwtAuthAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthAutoConfiguration.class);

    private final JwtProperties jwtProperties;

    public JwtAuthAutoConfiguration(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        logger.info("JWT Authentication Auto Configuration initialized");
        logger.debug("JWT Configuration: enabled={}, cookieName={}, blacklistEnabled={}",
                jwtProperties.isEnabled(),
                jwtProperties.getCookieName(),
                jwtProperties.getBlacklist().isEnabled());

        // 필수 설정 검증
        validateConfiguration();
    }

    private void validateConfiguration() {
        JwtProperties.Cognito cognito = jwtProperties.getCognito();

        if (cognito.getRegion() == null || cognito.getRegion().trim().isEmpty()) {
            logger.warn("JWT Cognito region is not configured");
        }

        if (cognito.getUserPoolId() == null || cognito.getUserPoolId().trim().isEmpty()) {
            logger.warn("JWT Cognito user pool ID is not configured");
        }

        if (cognito.getClientId() == null || cognito.getClientId().trim().isEmpty()) {
            logger.warn("JWT Cognito client ID is not configured");
        }

        logger.info("JWT Cognito configuration validated - Region: {}, UserPoolId: {}, ClientId: {}",
                cognito.getRegion(),
                cognito.getUserPoolId(),
                cognito.getClientId() != null ? cognito.getClientId().substring(0, Math.min(8, cognito.getClientId().length())) + "..." : "null");

        // 블랙리스트 설정 검증
        if (jwtProperties.getBlacklist().isEnabled()) {
            JwtProperties.Blacklist.Redis redis = jwtProperties.getBlacklist().getRedis();
            logger.info("JWT Blacklist enabled - Redis: {}:{}/{}",
                    redis.getHost(), redis.getPort(), redis.getDatabase());
        }
    }
}