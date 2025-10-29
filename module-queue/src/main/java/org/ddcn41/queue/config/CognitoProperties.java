// module-queue/src/main/java/org/ddcn41/queue/config/CognitoProperties.java

package org.ddcn41.queue.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "auth.cognito")
@Deprecated(forRemoval = true)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class CognitoProperties {
    private boolean enabled = true;
    private String userPoolId;
    private String clientId;
    private String region;
}