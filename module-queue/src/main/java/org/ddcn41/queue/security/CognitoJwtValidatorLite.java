package org.ddcn41.queue.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;

import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CognitoJwtValidatorLite {

    private final String issuer;
    private final String appClientId;
    private final String jwksUrl;

    // 매우 단순한 로컬 캐시 (10분)
    private volatile long cacheExpireAt = 0L;
    private volatile JWKSet cached;

    public CognitoJwtValidatorLite(String issuer, String jwksUrl, String appClientId) {
        this.issuer = Objects.requireNonNull(issuer);
        this.appClientId = Objects.requireNonNull(appClientId);
        this.jwksUrl = Objects.requireNonNull(jwksUrl);
    }

    public UserInfo validateAccessToken(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);

        // alg 체크
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new IllegalArgumentException("Unsupported alg");
        }

        // kid로 JWKS 검색 → 서명 검증
        RSAKey rsaKey = resolveRsaKey(jwt.getHeader().getKeyID());
        JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
        if (!jwt.verify(verifier)) {
            throw new IllegalArgumentException("Invalid signature");
        }

        // 클레임 검증
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        if (!issuer.equals(claims.getIssuer())) {
            throw new IllegalArgumentException("Invalid iss");
        }
        if (claims.getExpirationTime() == null ||
                Instant.now().isAfter(claims.getExpirationTime().toInstant())) {
            throw new IllegalArgumentException("Expired");
        }

        if (claims.getAudience() == null || !claims.getAudience().contains(appClientId)) {
            throw new IllegalArgumentException("Invalid aud");
        }
        Object tokenUse = claims.getClaim("token_use");
        if (!"access".equals(tokenUse)) {
            throw new IllegalArgumentException("Not access token");
        }

        // 필요한 값만 추출
        String sub = claims.getSubject();
        String username = asString(claims.getClaim("cognito:username"));
        List<String> groups = asStringList(claims.getClaim("cognito:groups"));

        // (있다면) 커스텀 userId 사용
        Long userId = null;
        Object uidObj = claims.getClaim("userId");
        if (uidObj != null) {
            try {
                userId = Long.valueOf(String.valueOf(uidObj));
            } catch (Exception ignore) {
            }
        }

        return new UserInfo(sub, username, groups, userId);
    }

    private RSAKey resolveRsaKey(String kid) throws Exception {
        JWKSet jwkSet = getCachedJwkSet();
        List<JWK> matches = new JWKSelector(new JWKMatcher.Builder()
                .keyID(kid).keyType(KeyType.RSA).build()).select(jwkSet);
        if (matches.isEmpty()) throw new IllegalArgumentException("JWK not found (kid=" + kid + ")");
        return (RSAKey) matches.get(0);
    }

    private synchronized JWKSet getCachedJwkSet() throws Exception {
        long now = System.currentTimeMillis();
        if (cached == null || now > cacheExpireAt) {
            cached = JWKSet.load(new URL(jwksUrl));
            cacheExpireAt = now + 10 * 60 * 1000; // 10분
        }
        return cached;
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object v) {
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object e : list) out.add(String.valueOf(e));
            return out;
        }
        return Collections.emptyList();
    }

    @Getter
    public static class UserInfo {
        private final String sub;
        private final String username;     // 없으면 null
        private final List<String> groups; // 없으면 []
        private final Long userId;         // 커스텀 클레임 없으면 null

        public UserInfo(String sub, String username, List<String> groups, Long userId) {
            this.sub = sub;
            this.username = username;
            this.groups = groups == null ? Collections.emptyList() : groups;
            this.userId = userId;
        }
    }
}
