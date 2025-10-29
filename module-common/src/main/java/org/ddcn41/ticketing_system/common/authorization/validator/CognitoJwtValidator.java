package org.ddcn41.ticketing_system.common.authorization.validator;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.ticketing_system.common.config.CognitoProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Deprecated(forRemoval = true)
@Service
@Slf4j
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
//@ConditionalOnProperty(name = "auth.cognito.enabled", havingValue = "true")
public class CognitoJwtValidator {

    private static final int CACHE_SIZE = 10;
    private static final int CACHE_DURATION_HOURS = 24;
    private static final int RATE_LIMIT_REQUESTS = 10;
    private static final int RATE_LIMIT_DURATION_MINUTES = 1;
    private static final long MILLIS_TO_SECONDS = 1000L;

    private final CognitoProperties properties;
    private JwkProvider jwkProvider;
    private String issuerUrl;

    public CognitoJwtValidator(CognitoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.issuerUrl = String.format(
                "https://cognito-idp.%s.amazonaws.com/%s",
                properties.getRegion(),
                properties.getUserPoolId()
        );

        this.jwkProvider = new JwkProviderBuilder(issuerUrl)
                .cached(CACHE_SIZE, CACHE_DURATION_HOURS, TimeUnit.HOURS)
                .rateLimited(RATE_LIMIT_REQUESTS, RATE_LIMIT_DURATION_MINUTES, TimeUnit.MINUTES)
                .build();

        log.info("Cognito JWKS provider initialized for User Pool: {} in region: {}",
                properties.getUserPoolId(), properties.getRegion());
    }

    public CognitoUserInfo validateAccessToken(String accessToken) {
        try {
            DecodedJWT jwt = JWT.decode(accessToken);

            var jwk = jwkProvider.get(jwt.getKeyId());

            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(issuerUrl)
                    .withClaim("client_id", properties.getClientId())
                    .withClaim("token_use", "access")
                    .build();

            DecodedJWT verifiedJWT = verifier.verify(accessToken);

            return CognitoUserInfo.builder()
                    .sub(verifiedJWT.getSubject())
                    .username(verifiedJWT.getClaim("username").asString())
                    .email(verifiedJWT.getClaim("email").asString())
                    .groups(extractGroupsFromToken(verifiedJWT))
                    .tokenExp(verifiedJWT.getExpiresAt().getTime() / 1000)
                    .build();

        } catch (Exception e) {
            log.error("Cognito token validation failed: {}", e.getMessage());
            return null;
        }
    }

    private List<String> extractGroupsFromToken(DecodedJWT jwt) {
        return Optional.ofNullable(
                jwt.getClaim("cognito:groups").asList(String.class)
        ).orElse(Collections.emptyList());
    }

    @Builder
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CognitoUserInfo {
        private String sub;
        private String username;
        private String email;
        private List<String> groups;
        private Long tokenExp;
    }
}
