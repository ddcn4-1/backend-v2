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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        jwtlogger.info("=== JWT Filter processing: {} ===", requestURI);


        try {
            // JWT 토큰 추출
            String jwt = extractJwtFromRequest(request);
            jwtlogger.info("JWT Token extracted: {}", jwt != null ? "Present" : "Not found");

            if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 블랙리스트 확인
                if (isTokenBlacklisted(jwt)) {
                    jwtlogger.debug("Token is blacklisted, rejecting request");
                    handleAuthenticationFailure(response, "Token is blacklisted");
                    return;
                }

                // JWT 검증 및 사용자 정보 생성
                BasicCognitoUser userDetails = jwtValidator.validateTokenAndCreateUser(jwt);

                // Authentication 객체 생성
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                jwtlogger.info("Authentication set successfully for user: {}", userDetails.getUsername());
                jwtlogger.info("Authorities for {}: {}", userDetails.getUsername(), userDetails.getAuthorities());
                // SecurityContext에 인증 정보 설정
                SecurityContextHolder.getContext().setAuthentication(authToken);

                jwtlogger.debug("JWT authentication successful for user: {}", userDetails.getUsername());

                // 요청 속성에 사용자 정보 추가 (필요시)
                request.setAttribute("currentUser", userDetails);
            }

        } catch (JwtException e) {
            jwtlogger.debug("JWT validation failed: {}", e.getMessage());
            // 인증 실패시에도 필터 체인을 계속 진행 (익명 사용자로 처리)
            SecurityContextHolder.clearContext();

        } catch (Exception e) {
            jwtlogger.error("Unexpected error in JWT authentication filter: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        // 다음 필터로 계속 진행
        filterChain.doFilter(request, response);
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
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // Health check, 정적 리소스 등은 제외
        return path.startsWith("/actuator/") ||
                path.startsWith("/health") ||
                path.startsWith("/static/") ||
                path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.equals("/favicon.ico");
    }
}
