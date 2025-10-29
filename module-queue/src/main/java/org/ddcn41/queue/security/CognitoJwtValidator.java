// module-queue/src/main/java/org/ddcn41/queue/security/CognitoJwtValidator.java

package org.ddcn41.queue.security;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.queue.config.CognitoProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Deprecated(forRemoval = true)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class CognitoJwtValidator {

    private final CognitoProperties properties;
    private JwkProvider jwkProvider;
    private String issuerUrl;

    public CognitoJwtValidator(CognitoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.warn("⚠️ Cognito validation is DISABLED");
            return;
        }

        this.issuerUrl = String.format(
                "https://cognito-idp.%s.amazonaws.com/%s",
                properties.getRegion(),
                properties.getUserPoolId()
        );

        this.jwkProvider = new JwkProviderBuilder(issuerUrl)
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build();

        log.info("✅ Cognito JWKS initialized - UserPool: {}, Region: {}",
                properties.getUserPoolId(), properties.getRegion());
    }

    /**
     *  Access Token 검증
     */
    public CognitoUserInfo validateAccessToken(String accessToken) {
        try {
            DecodedJWT jwt = JWT.decode(accessToken);

            // 1. 서명 검증
            var jwk = jwkProvider.get(jwt.getKeyId());
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(issuerUrl)
                    .withClaim("client_id", properties.getClientId())
                    .withClaim("token_use", "access")
                    .build();

            DecodedJWT verifiedJWT = verifier.verify(accessToken);

            // 2. 기본 정보 추출
            String sub = verifiedJWT.getSubject();
            String username = verifiedJWT.getClaim("username").asString();

            // 3. 그룹 추출
            List<String> groups = verifiedJWT.getClaim("cognito:groups").asList(String.class);
            if (groups == null) groups = List.of();

            log.debug("✅ Token validated - sub: {}, username: {}", sub, username);

            return new CognitoUserInfo(sub, username, groups);

        } catch (Exception e) {
            log.error("❌ Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     *  최소 UserInfo (userId 없음)
     */
    @Getter
    public static class CognitoUserInfo {
        private final String sub;
        private final String username;
        private final List<String> groups;

        public CognitoUserInfo(String sub, String username, List<String> groups) {
            this.sub = sub;
            this.username = username;
            this.groups = groups;
        }
    }
}