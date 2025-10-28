package org.ddcn41.starter.authorization.validator;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.security.Key;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CognitoSigningKeyResolver extends SigningKeyResolverAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CognitoSigningKeyResolver.class);

    private final JwtProperties jwtProperties;
    private final ConcurrentHashMap<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile JWKSet jwkSet;
    private volatile long lastFetchTime = 0;

    public CognitoSigningKeyResolver(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Key resolveSigningKey(JwsHeader jwsHeader, Claims claims) {
        String keyId = jwsHeader.getKeyId();

        if (keyId == null) {
            throw new IllegalArgumentException("JWT header must contain 'kid' (key ID)");
        }

        return getPublicKey(keyId);
    }

    private RSAPublicKey getPublicKey(String keyId) {
        // 캐시에서 먼저 확인
        RSAPublicKey cachedKey = keyCache.get(keyId);
        if (cachedKey != null && !isJwkSetExpired()) {
            return cachedKey;
        }

        try {
            // JWKS 새로 가져오기
            if (jwkSet == null || isJwkSetExpired()) {
                fetchJwkSet();
            }

            JWK jwk = jwkSet.getKeyByKeyId(keyId);
            if (jwk == null) {
                // JWKS 재시도 (키 갱신된 경우)
                logger.info("Key not found in cache, refetching JWKS for keyId: {}", keyId);
                fetchJwkSet();
                jwk = jwkSet.getKeyByKeyId(keyId);

                if (jwk == null) {
                    throw new IllegalArgumentException("Unable to find key with ID: " + keyId);
                }
            }

            RSAKey rsaKey = jwk.toRSAKey();
            RSAPublicKey publicKey = rsaKey.toRSAPublicKey();

            // 캐시에 저장
            keyCache.put(keyId, publicKey);

            logger.debug("Successfully resolved public key for keyId: {}", keyId);
            return publicKey;

        } catch (Exception e) {
            logger.error("Failed to get public key for keyId: {}", keyId, e);
            throw new RuntimeException("Failed to resolve signing key", e);
        }
    }

    private synchronized void fetchJwkSet() throws Exception {
        // Double-checked locking
        if (!isJwkSetExpired() && jwkSet != null) {
            return;
        }

        String jwksUrl = jwtProperties.getCognito().getEffectiveJwksUrl();
        logger.debug("Fetching JWKS from: {}", jwksUrl);

        try {
            this.jwkSet = JWKSet.load(new URL(jwksUrl));
            this.lastFetchTime = System.currentTimeMillis();

            logger.info("JWKS fetched successfully from: {}", jwksUrl);

        } catch (Exception e) {
            logger.error("Failed to fetch JWKS from: {}", jwksUrl, e);
            throw new RuntimeException("Failed to fetch JWKS", e);
        }
    }

    private boolean isJwkSetExpired() {
        return System.currentTimeMillis() - lastFetchTime > jwtProperties.getJwksCacheDuration();
    }

    /**
     * 캐시 정리 (필요시 사용)
     */
    public void clearCache() {
        keyCache.clear();
        jwkSet = null;
        lastFetchTime = 0;
        logger.info("JWKS cache cleared");
    }
}