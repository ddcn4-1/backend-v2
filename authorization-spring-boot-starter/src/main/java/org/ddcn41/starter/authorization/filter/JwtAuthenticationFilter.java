package org.ddcn41.starter.authorization.filter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ddcn41.starter.authorization.model.BasicCognitoUser;
import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.ddcn41.starter.authorization.service.TokenBlacklistService;
import org.ddcn41.starter.authorization.validator.CognitoJwtValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger jwtlogger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProperties jwtProperties;
    private final CognitoJwtValidator jwtValidator;
    private final Optional<TokenBlacklistService> blacklistService;

    public JwtAuthenticationFilter(JwtProperties jwtProperties,
                                   CognitoJwtValidator jwtValidator,
                                   Optional<TokenBlacklistService> blacklistService) {
        this.jwtProperties = jwtProperties;
        this.jwtValidator = jwtValidator;
        this.blacklistService = blacklistService;
    }

    // JwtAuthenticationFilter.java - doFilterInternal 메서드만 수정

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        jwtlogger.info("=== JWT Filter processing: {} ===", requestURI);

        try {
            String jwt = extractJwtFromRequest(request);
            jwtlogger.info("JWT Token extracted: {}", jwt != null ? "Present" : "Not found");

            if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                //  Mock JWT 패턴 감지 (부하테스트용)
                if (jwt.contains("mock-signature-for-load-test")) {
                    jwtlogger.info("Mock JWT 감지됨 - 테스트 사용자 생성");
                    BasicCognitoUser mockUser = createMockUserFromJWT(jwt);
                    setAuthentication(mockUser, request);
                } else {
                    // 기존 Cognito JWT 검증 로직
                    if (isTokenBlacklisted(jwt)) {
                        jwtlogger.debug("Token is blacklisted, rejecting request");
                        handleAuthenticationFailure(response, "Token is blacklisted");
                        return;
                    }

                    BasicCognitoUser userDetails = jwtValidator.validateTokenAndCreateUser(jwt);
                    setAuthentication(userDetails, request);
                }
            }

        } catch (JwtException e) {
            jwtlogger.debug("JWT validation failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            jwtlogger.error("Unexpected error in JWT authentication filter: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    //  Mock 사용자 생성 메서드 (BasicCognitoUser 생성자에 맞춤)
    private BasicCognitoUser createMockUserFromJWT(String jwt) {
        try {
            // JWT payload 디코딩
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(java.util.Base64.getDecoder().decode(parts[1]));
                jwtlogger.info("🔍 Mock JWT Payload: {}", payload);

                // 간단한 JSON 파싱으로 userId 추출
                String userId = extractValueFromJson(payload, "userId");
                String username = extractValueFromJson(payload, "username");

                if (userId == null) userId = "mock-user-" + System.currentTimeMillis();
                if (username == null) username = userId;

                jwtlogger.info("✅ Mock 사용자 생성: userId={}, username={}", userId, username);

                // BasicCognitoUser의 실제 생성자 사용 (Claims 기반)
                // 빈 Claims 객체 생성
                io.jsonwebtoken.Claims mockClaims = io.jsonwebtoken.Jwts.claims();
                mockClaims.setSubject(userId);
                mockClaims.put("cognito:username", username);
                mockClaims.put("userId", userId);

                return new BasicCognitoUser(mockClaims, userId);
            }
        } catch (Exception e) {
            jwtlogger.warn("Mock JWT 파싱 실패, 기본 사용자 생성: {}", e.getMessage());
        }

        // 파싱 실패 시 기본 Mock 사용자
        io.jsonwebtoken.Claims defaultClaims = io.jsonwebtoken.Jwts.claims();
        defaultClaims.setSubject("default-mock-user");
        defaultClaims.put("cognito:username", "default-mock-user");
        defaultClaims.put("userId", "default-mock-user");

        return new BasicCognitoUser(defaultClaims, "default-mock-user");
    }

    //  간단한 JSON 값 추출 유틸리티
    private String extractValueFromJson(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int startIndex = json.indexOf(searchKey);
            if (startIndex != -1) {
                startIndex += searchKey.length();
                int endIndex = json.indexOf("\"", startIndex);
                if (endIndex != -1) {
                    return json.substring(startIndex, endIndex);
                }
            }
        } catch (Exception e) {
            jwtlogger.debug("JSON 값 추출 실패: key={}, error={}", key, e.getMessage());
        }
        return null;
    }

    //  Authentication 설정 메서드
    private void setAuthentication(BasicCognitoUser userDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        request.setAttribute("currentUser", userDetails);

        jwtlogger.info("✅✅✅ Authentication set successfully for user: {}", userDetails.getUsername());
        jwtlogger.info(" Authorities: {}", userDetails.getAuthorities());
    }

    /**
     * HTTP 요청에서 JWT 토큰 추출
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        // 1. Authorization 헤더에서 추출
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        // 2. 쿠키에서 추출
        String jwtFromCookie = extractJwtFromCookie(request);
        if (StringUtils.hasText(jwtFromCookie)) {
            return jwtFromCookie;
        }
        return null;
    }

    /**
     * 쿠키에서 JWT 토큰 추출
     */
    private String extractJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        String cookieName = jwtProperties.getCookieName();

        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * 토큰 블랙리스트 확인
     */
    private boolean isTokenBlacklisted(String jwt) {
        return blacklistService.map(service -> service.isBlacklisted(jwt)).orElse(false);
    }

    /**
     * 인증 실패 처리
     */
    private void handleAuthenticationFailure(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = String.format(
                "{\"error\": \"Unauthorized\", \"message\": \"%s\", \"timestamp\": \"%s\"}",
                message,
                java.time.Instant.now().toString()
        );

        response.getWriter().write(jsonResponse);
    }

    /**
     * 특정 URL 패턴은 JWT 검증에서 제외할지 결정
     */
    /**
     * 특정 URL 패턴은 JWT 검증에서 제외할지 결정
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        jwtlogger.debug(" JWT Filter 체크: {} {}", method, path);

        // 기존 제외 경로들
        boolean isStaticResource = path.startsWith("/actuator/") ||
                path.startsWith("/health") ||
                path.startsWith("/static/") ||
                path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.equals("/favicon.ico");

        if (isStaticResource) {
            jwtlogger.debug(" 정적 리소스 - JWT 검증 제외: {}", path);
            return true;
        }

        //  인증이 필요없는 Queue API들 제외
        boolean isPublicQueueApi =
                // 토큰 상태 조회 (GET /v1/queue/status/{token})
                (path.matches("/v1/queue/status/.*") && "GET".equals(method)) ||

                        // 토큰 검증 (POST /v1/queue/token/{token}/verify)
                        (path.matches("/v1/queue/token/.*/verify") && "POST".equals(method)) ||

                        // 토큰 사용 완료 (POST /v1/queue/token/{token}/use)
                        (path.matches("/v1/queue/token/.*/use") && "POST".equals(method)) ||

                        // 세션 해제 (POST /v1/queue/release-session)
                        (path.equals("/v1/queue/release-session") && "POST".equals(method));

        if (isPublicQueueApi) {
            jwtlogger.info(" 공개 Queue API - JWT 검증 제외: {} {}", method, path);
            return true;
        }

        // 인증이 필요한 Queue API들
        boolean isPrivateQueueApi =
                path.equals("/v1/queue/check") ||
                        path.equals("/v1/queue/token") ||
                        path.equals("/v1/queue/activate") ||
                        path.equals("/v1/queue/my-tokens") ||
                        path.equals("/v1/queue/heartbeat") ||
                        path.matches("/v1/queue/token/.*") ||  // DELETE /v1/queue/token/{token}
                        path.equals("/v1/queue/session-info") ||
                        path.equals("/v1/queue/clear-sessions");

        if (isPrivateQueueApi) {
            jwtlogger.info(" 인증 필요한 Queue API - JWT 검증 필요: {} {}", method, path);
            return false;  // JWT 검증 필요
        }

        jwtlogger.debug(" 기타 경로 - JWT 검증 필요: {} {}", method, path);
        return false;  // 기본적으로 JWT 검증 필요
    }
}
