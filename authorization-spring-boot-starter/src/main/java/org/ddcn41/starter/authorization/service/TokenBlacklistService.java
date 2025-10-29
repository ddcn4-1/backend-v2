package org.ddcn41.starter.authorization.service;

import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.ddcn41.starter.authorization.validator.CognitoJwtValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Date;

public class TokenBlacklistService {
    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProperties jwtProperties;
    private final CognitoJwtValidator jwtValidator;

    public TokenBlacklistService(RedisTemplate<String, String> redisTemplate,
                                 JwtProperties jwtProperties,
                                 CognitoJwtValidator jwtValidator) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
        this.jwtValidator = jwtValidator;
    }

    /**
     * 토큰을 블랙리스트에 추가
     */
    public void blacklistToken(String token) {
        try {
            String tokenKey = generateKey(token);

            // 토큰의 만료 시간까지만 블랙리스트에 유지
            Date expiration = jwtValidator.getTokenExpiration(token);
            if (expiration != null && expiration.after(new Date())) {
                long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

                redisTemplate.opsForValue().set(tokenKey, "blacklisted", Duration.ofSeconds(ttlSeconds));

                logger.info("Token blacklisted successfully with TTL: {} seconds", ttlSeconds);
            } else {
                // 이미 만료된 토큰이거나 만료 시간을 알 수 없는 경우, 기본 TTL 적용
                redisTemplate.opsForValue().set(tokenKey, "blacklisted", Duration.ofDays(1));

                logger.warn("Token expiration unknown or already expired, using default TTL");
            }

        } catch (Exception e) {
            logger.error("Failed to blacklist token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to blacklist token", e);
        }
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     */
    public boolean isBlacklisted(String token) {
        try {
            String tokenKey = generateKey(token);

            boolean blacklisted = redisTemplate.hasKey(tokenKey);

            if (blacklisted) {
                logger.debug("Token found in blacklist");
            }

            return blacklisted;

        } catch (Exception e) {
            logger.error("Failed to check token blacklist status: {}", e.getMessage(), e);
            // Redis 오류시 보안상 블랙리스트에 있는 것으로 간주
            return true;
        }
    }

    /**
     * 사용자의 모든 토큰을 블랙리스트에 추가 (로그아웃 올 사용자 세션 무효화)
     */
    public void blacklistAllUserTokens(String userId) {
        try {
            String userKey = generateUserKey(userId);

            // 사용자별 블랙리스트 마커 설정 (24시간)
            redisTemplate.opsForValue().set(userKey, String.valueOf(System.currentTimeMillis()), Duration.ofDays(1));

            logger.info("All tokens blacklisted for user: {}", userId);

        } catch (Exception e) {
            logger.error("Failed to blacklist all user tokens for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to blacklist all user tokens", e);
        }
    }

    /**
     * 토큰의 사용자가 전체 로그아웃 상태인지 확인
     */
    public boolean isUserBlacklisted(String token) {
        try {
            String userId = jwtValidator.extractUserId(token);
            if (userId == null) {
                return true; // 사용자 ID를 추출할 수 없으면 블랙리스트 처리
            }

            String userKey = generateUserKey(userId);
            String blacklistTime = redisTemplate.opsForValue().get(userKey);

            if (blacklistTime != null) {
                // 토큰 발행 시간과 블랙리스트 시간 비교
                try {
                    Date tokenIssuedAt = jwtValidator.validateToken(token).getIssuedAt();
                    long blacklistTimestamp = Long.parseLong(blacklistTime);

                    return tokenIssuedAt.getTime() < blacklistTimestamp;

                } catch (Exception e) {
                    logger.debug("Failed to compare token issue time with blacklist time: {}", e.getMessage());
                    return true; // 비교 실패시 블랙리스트 처리
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("Failed to check user blacklist status: {}", e.getMessage(), e);
            return true; // 오류시 블랙리스트 처리
        }
    }

    /**
     * 토큰을 블랙리스트에서 제거 (일반적으로 사용되지 않음)
     */
    public void removeFromBlacklist(String token) {
        try {
            String tokenKey = generateKey(token);
            redisTemplate.delete(tokenKey);

            logger.info("Token removed from blacklist");

        } catch (Exception e) {
            logger.error("Failed to remove token from blacklist: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove token from blacklist", e);
        }
    }

    /**
     * Redis 연결 상태 확인
     */
    public boolean isRedisAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            logger.warn("Redis connection check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰별 Redis 키 생성
     */
    private String generateKey(String token) {
        // 토큰 해시를 사용하여 키 길이 제한
        String tokenHash = String.valueOf(token.hashCode());
        return jwtProperties.getBlacklist().getRedis().getKeyPrefix() + "token:" + tokenHash;
    }

    /**
     * 사용자별 Redis 키 생성
     */
    private String generateUserKey(String userId) {
        return jwtProperties.getBlacklist().getRedis().getKeyPrefix() + "user:" + userId;
    }
}
