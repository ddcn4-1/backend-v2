package org.ddcn41.starter.authorization.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NestedConfigurationProperty
    private Cognito cognito = new Cognito();

    @NestedConfigurationProperty
    private Blacklist blacklist = new Blacklist();

    private String cookieName = "jwt-token";
    private boolean enabled = true;
    private long jwksCacheDuration = 300000; // 5분 (밀리초)

    // Getters and Setters
    public Cognito getCognito() {
        return cognito;
    }

    public void setCognito(Cognito cognito) {
        this.cognito = cognito;
    }

    public Blacklist getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(Blacklist blacklist) {
        this.blacklist = blacklist;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getJwksCacheDuration() {
        return jwksCacheDuration;
    }

    public void setJwksCacheDuration(long jwksCacheDuration) {
        this.jwksCacheDuration = jwksCacheDuration;
    }

    public static class Cognito {
        private String region;
        private String userPoolId;
        private String clientId;
        private String jwksUrl;
        private boolean validateIssuer = true;
        private boolean validateAudience = true;
        private boolean validateTokenUse = true;

        // Getters and Setters
        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getUserPoolId() {
            return userPoolId;
        }

        public void setUserPoolId(String userPoolId) {
            this.userPoolId = userPoolId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getJwksUrl() {
            return jwksUrl;
        }

        public void setJwksUrl(String jwksUrl) {
            this.jwksUrl = jwksUrl;
        }

        public boolean isValidateIssuer() {
            return validateIssuer;
        }

        public void setValidateIssuer(boolean validateIssuer) {
            this.validateIssuer = validateIssuer;
        }

        public boolean isValidateAudience() {
            return validateAudience;
        }

        public void setValidateAudience(boolean validateAudience) {
            this.validateAudience = validateAudience;
        }

        public boolean isValidateTokenUse() {
            return validateTokenUse;
        }

        public void setValidateTokenUse(boolean validateTokenUse) {
            this.validateTokenUse = validateTokenUse;
        }

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

    public static class Blacklist {
        private boolean enabled = true;

        @NestedConfigurationProperty
        private Redis redis = new Redis();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Redis getRedis() {
            return redis;
        }

        public void setRedis(Redis redis) {
            this.redis = redis;
        }

        public static class Redis {
            private String host = "localhost";
            private int port = 6379;
            private String password;
            private int database = 0;
            private String keyPrefix = "jwt:blacklist:";
            private long connectionTimeout = 2000; // 2초
            private long commandTimeout = 1000; // 1초

            // Getters and Setters
            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }

            public int getPort() {
                return port;
            }

            public void setPort(int port) {
                this.port = port;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password;
            }

            public int getDatabase() {
                return database;
            }

            public void setDatabase(int database) {
                this.database = database;
            }

            public String getKeyPrefix() {
                return keyPrefix;
            }

            public void setKeyPrefix(String keyPrefix) {
                this.keyPrefix = keyPrefix;
            }

            public long getConnectionTimeout() {
                return connectionTimeout;
            }

            public void setConnectionTimeout(long connectionTimeout) {
                this.connectionTimeout = connectionTimeout;
            }

            public long getCommandTimeout() {
                return commandTimeout;
            }

            public void setCommandTimeout(long commandTimeout) {
                this.commandTimeout = commandTimeout;
            }
        }
    }
}