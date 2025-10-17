package org.ddcn41.queue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.queue.domain.CustomUserDetails;
import org.ddcn41.ticketing_system.auth.utils.JwtUtil;
import org.ddcn41.ticketing_system.common.config.CognitoProperties;
import org.ddcn41.ticketing_system.common.service.CognitoJwtValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * JWT 인증 필터
 *
 * 토큰 추출 우선순위:
 * 1. Authorization 헤더 (Bearer 토큰)
 * 2. Cookie (access_token)
 *
 * 토큰 검증 방식:
 * 1. Cognito 활성화 시 → JWKS 검증 (CognitoJwtValidator)
 * 2. Cognito 비활성화 시 → 기존 JWT 검증 (JwtUtil)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CognitoProperties cognitoProperties;

    @Autowired(required = false)
    private CognitoJwtValidator cognitoJwtValidator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        log.debug("=== JWT Filter 시작 ===");
        log.debug("Request URI: {}", request.getRequestURI());
        log.debug("Authorization Header: {}", request.getHeader("Authorization"));

        try {
            String token = extractToken(request);

            if (token != null) {
                log.debug("Token 추출 성공 (길이: {})", token.length());

                // Cognito 토큰 검증
                if (cognitoProperties.isEnabled() && cognitoJwtValidator != null) {
                    authenticateWithCognito(token, request);
                } else {
                    // 기존 JWT 검증
                    authenticateWithJwt(token, request);
                }
            } else {
                log.debug("Token이 없습니다");
            }
        } catch (Exception e) {
            log.error("JWT 인증 실패: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 토큰 추출 (우선순위: Authorization 헤더 → Cookie)
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Authorization 헤더 확인
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            log.debug("Token 추출: Authorization 헤더");
            return bearerToken.substring(7);
        }

        // 2. Cookie 확인 (module-auth 호환)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    log.debug("Token 추출: Cookie (access_token)");
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Cognito JWT 검증 (JWKS 기반)
     */
    private void authenticateWithCognito(String token, HttpServletRequest request) {
        try {
            CognitoJwtValidator.CognitoUserInfo userInfo = cognitoJwtValidator.validateAccessToken(token);

            if (userInfo == null) {
                log.warn("Cognito 토큰 검증 실패");
                return;
            }

            log.info("Cognito 인증 성공 - sub: {}, username: {}", userInfo.getSub(), userInfo.getUsername());

            // TODO: Cognito sub를 userId로 매핑하는 로직 필요
            // 현재는 임시로 sub를 사용 (실제로는 DB 조회 필요)
            Long userId = 1L; // 임시

            // 권한 설정
            var authorities = userInfo.getGroups().stream()
                    .map(group -> new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                    .collect(Collectors.toList());

            if (authorities.isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }

            CustomUserDetails userDetails = new CustomUserDetails(
                    userInfo.getUsername(),
                    "",
                    authorities,
                    userId
            );

            setAuthentication(userDetails, request);

        } catch (Exception e) {
            log.error("Cognito 인증 처리 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 기존 JWT 검증 (HMAC 기반)
     */
    private void authenticateWithJwt(String token, HttpServletRequest request) {
        try {
            String username = jwtUtil.extractUsername(token);
            Long userId = jwtUtil.extractUserId(token);

            if (userId == null) {
                log.warn("JWT에 userId가 없습니다");
                return;
            }

            if (jwtUtil.isTokenExpired(token)) {
                log.warn("JWT 토큰이 만료되었습니다");
                return;
            }

            log.info("JWT 인증 성공 - username: {}, userId: {}", username, userId);

            CustomUserDetails userDetails = new CustomUserDetails(
                    username,
                    "",
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                    userId
            );

            setAuthentication(userDetails, request);

        } catch (Exception e) {
            log.error("JWT 인증 처리 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * SecurityContext에 인증 정보 설정
     */
    private void setAuthentication(CustomUserDetails userDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("SecurityContext에 인증 정보 설정 완료");
    }
}
