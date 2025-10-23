package org.ddcn41.ticketing_system.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "auth.cognito")
public class CognitoProperties {
    private boolean enabled = true;  // false : 기존 JWT 방식 유지
    private String userPoolId;
    private String clientId;
    private String region;
}
