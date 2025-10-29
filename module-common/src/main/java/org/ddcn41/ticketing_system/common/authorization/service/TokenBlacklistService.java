package org.ddcn41.ticketing_system.common.authorization.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.ticketing_system.common.authorization.interfaces.TokenBlacklistChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;


@Deprecated(forRemoval = true)
@Slf4j
@Service
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class TokenBlacklistService implements TokenBlacklistChecker {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_KEY_PREFIX = "blacklisted_token:";

    /**
     * 토큰을 블랙리스트에 추가
     */
    public void blacklistToken(String token, long expirationTimeMs) {
        String key = BLACKLIST_KEY_PREFIX + token;

        // Redis에 저장 (토큰 만료 시간까지만 저장)
        Duration ttl = Duration.ofMillis(expirationTimeMs - System.currentTimeMillis());

        if (ttl.toSeconds() > 0) {
            redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), ttl);
        }
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     */

    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_KEY_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 사용자의 모든 토큰 무효화 (선택사항)
     */
    public void blacklistAllUserTokens(String username) {
        // username을 키로 하는 블랙리스트 (구현 방법은 다양함)
        String userKey = "blacklisted_user:" + username;
        redisTemplate.opsForValue().set(userKey, LocalDateTime.now().toString(), Duration.ofHours(24));
    }

    @Override
    public void addToBlacklist(String token) {
        try {
            // JWT에서 만료시간 추출
            DecodedJWT jwt = JWT.decode(token);
            long expirationTimeMs = jwt.getExpiresAt().getTime();

            // 기존 메서드 재사용
            blacklistToken(token, expirationTimeMs);

            log.info("Token added to blacklist (auto-expiry)");
        } catch (Exception e) {
            log.error("Failed to add token to blacklist", e);
        }
    }

    /**
     * TokenBlacklistChecker 인터페이스 구현
     * TTL을 직접 지정해서 블랙리스트에 추가
     */
    @Override
    public void addToBlacklist(String token, long ttlSeconds) {
        try {
            String key = BLACKLIST_KEY_PREFIX + token;
            redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), Duration.ofSeconds(ttlSeconds));
            log.info("Token added to blacklist with TTL: {} seconds", ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to add token to blacklist with TTL", e);
        }
    }

    /**
     * Cognito 토큰 전용 편의 메서드
     */
    public void blacklistCognitoToken(String accessToken) {
        addToBlacklist(accessToken);  // 자동으로 만료시간 추출
        log.info("Cognito access token blacklisted");
    }
}