package org.ddcn41.ticketing_system.auth.deprecated.service;

import lombok.extern.slf4j.Slf4j;
import org.ddcn41.ticketing_system.auth.deprecated.dto.response.LogoutResponse;
import org.ddcn41.ticketing_system.auth.deprecated.exception.TokenProcessingException;
import org.ddcn41.ticketing_system.common.authorization.service.TokenBlacklistService;
import org.ddcn41.ticketing_system.common.authorization.util.JwtUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Deprecated
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class AuthService {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public LogoutResponse processLogout(String token, String username) {
        if (token != null) {
            try {
                long expirationTime = jwtUtil.extractClaims(token).getExpiration().getTime();
                tokenBlacklistService.blacklistToken(token, expirationTime);
                log.info("JWT token blacklisted for user: {}", username);

                // 토큰 만료 시간 계산
                long timeLeft = (expirationTime - System.currentTimeMillis()) / 1000 / 60;

                return LogoutResponse.forJwtLogout(username, timeLeft + "분");
            } catch (Exception e) {
                // 서비스에서는 예외를 그대로 던져서 컨트롤러에서 처리하도록 함
                throw new TokenProcessingException("토큰 처리 중 오류 발생: " + e.getMessage(), e);
            }
        }

        return new LogoutResponse(username);
    }

    public LogoutResponse processCognitoLogout(String cognitoToken, String username) {
        try {
            // Cognito 토큰을 블랙리스트에 추가
            tokenBlacklistService.addToBlacklist(cognitoToken);
            log.info("Cognito token blacklisted for user: {}", username);

            return LogoutResponse.forCognitoUser(username, "Cognito 로그아웃 처리 완료");

        } catch (Exception e) {
            throw new TokenProcessingException("Cognito 토큰 처리 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
