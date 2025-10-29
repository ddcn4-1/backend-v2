package org.ddcn41.starter.authorization.properties;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Setter
@Getter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // Getters and Setters
    @NestedConfigurationProperty
    private Cognito cognito = new Cognito();

    @NestedConfigurationProperty
    private Blacklist blacklist = new Blacklist();

    private String cookieName = "jwt-token";
    private boolean enabled = true;
    private long jwksCacheDuration = 300000; // 5분 (밀리초)

    @Setter
    @Getter
    public static class Cognito {
        // Getters and Setters
        private String region;
        private String userPoolId;
        private String clientId;
        private String jwksUrl;
        private boolean validateIssuer = true;
        private boolean validateAudience = true;
        private boolean validateTokenUse = true;

        // 편의 메서드: 기본 JWKS URL 생성
        public String getEffectiveJwksUrl() {
            if (jwksUrl != null && !jwksUrl.trim().isEmpty()) {
                return jwksUrl;
            }

            if (region == null || userPoolId == null) {
                throw new IllegalStateException("Region and UserPoolId must be configured");
            }

            return String.format("https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json",
                    region, userPoolId);
        }

        // 편의 메서드: 기본 Issuer URL 생성
        public String getExpectedIssuer() {
            if (region == null || userPoolId == null) {
                throw new IllegalStateException("Region and UserPoolId must be configured");
            }

            return String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId);
        }
    }

    @Setter
    @Getter
    public static class Blacklist {
        private boolean enabled = true;

        @NestedConfigurationProperty
        private Redis redis = new Redis();

        @Setter
        @Getter
        public static class Redis {
            // Getters and Setters
            private String host = "localhost";
            private int port = 6379;
            private String password;
            private int database = 0;
            private String keyPrefix = "jwt:blacklist:";
            private long connectionTimeout = 2000; // 2초
            private long commandTimeout = 1000; // 1초

        }
    }
}