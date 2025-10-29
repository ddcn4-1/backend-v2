package org.ddcn41.ticketing_system.common.authorization.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.ticketing_system.common.authorization.interfaces.CustomUserDetailsProvider;
import org.ddcn41.ticketing_system.common.authorization.interfaces.JwtTokenValidator;
import org.ddcn41.ticketing_system.common.authorization.interfaces.TokenBlacklistChecker;
import org.ddcn41.ticketing_system.common.authorization.validator.CognitoJwtValidator;
import org.ddcn41.ticketing_system.common.config.CognitoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Deprecated(forRemoval = true)
@Slf4j
@Component
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator jwtTokenValidator;
    private final CustomUserDetailsProvider userDetailsProvider;
    private final TokenBlacklistChecker tokenBlacklistChecker;
    private final CognitoProperties cognitoProperties;

    @Autowired(required = false)
    private CognitoJwtValidator cognitoJwtValidator;

    public JwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator,
                                   CustomUserDetailsProvider userDetailsProvider,
                                   TokenBlacklistChecker tokenBlacklistChecker,
                                   CognitoProperties cognitoProperties) {
        this.jwtTokenValidator = jwtTokenValidator;
        this.userDetailsProvider = userDetailsProvider;
        this.tokenBlacklistChecker = tokenBlacklistChecker;
        this.cognitoProperties = cognitoProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        log.debug("Processing: {} {}", request.getMethod(), requestUri);

        if (shouldSkipAuthentication(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            performAuthentication(request);
        } catch (Exception e) {
            log.error("Authentication error for {}: {}", requestUri, e.getMessage());
            SecurityContextHolder.clearContext(); // 명시적으로 컨텍스트 클리어
        }

        filterChain.doFilter(request, response);
    }

    private void performAuthentication(HttpServletRequest request) {
        // 이미 인증된 경우 스킵
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        // 1. Cognito 인증 시도
        if (cognitoProperties.isEnabled() && cognitoJwtValidator != null) {
            String cognitoToken = extractTokenFromCookies(request, "access_token");
            if (cognitoToken != null && handleCognitoAuthentication(cognitoToken, request)) {
                log.debug("Cognito authentication successful");
                return;
            }
        }

        // 2. JWT 인증 시도
        String jwtToken = extractTokenFromHeader(request);
        if (jwtToken != null) {
            handleJwtAuthentication(jwtToken, request);
        }
    }

    private boolean handleCognitoAuthentication(String token, HttpServletRequest request) {
        try {
            if (tokenBlacklistChecker.isTokenBlacklisted(token)) {
                return false;
            }

            CognitoJwtValidator.CognitoUserInfo userInfo = cognitoJwtValidator.validateAccessToken(token);
            if (userInfo == null) {
                return false;
            }

            List<SimpleGrantedAuthority> authorities = userInfo.getGroups().stream()
                    .map(group -> new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                    .collect(Collectors.toList());

            if (authorities.isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(userInfo.getSub())    // username에 sub(userId) 주입
                    .password("")
                    .authorities(authorities)
                    .build();

            setAuthentication(userDetails, authorities, request);
            return true;

        } catch (Exception e) {
            log.error("Cognito authentication failed: {}", e.getMessage());
            return false;
        }
    }

    private void handleJwtAuthentication(String token, HttpServletRequest request) {
        try {
            if (tokenBlacklistChecker.isTokenBlacklisted(token)) {
                return;
            }

            String username = jwtTokenValidator.extractUsername(token);
            if (username == null) {
                return;
            }

            UserDetails userDetails = userDetailsProvider.loadUserByUsername(username);
            if (jwtTokenValidator.validateToken(token, username)) {
                setAuthentication(userDetails, userDetails.getAuthorities(), request);
                log.debug("JWT authentication successful: {}", username);
            }

        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
        }
    }

    private void setAuthentication(UserDetails userDetails,
                                   Collection<? extends GrantedAuthority> authorities,
                                   HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;
    }

    private String extractTokenFromCookies(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .filter(value -> !value.trim().isEmpty())
                .orElse(null);
    }

    private boolean shouldSkipAuthentication(String requestUri) {
        return requestUri.startsWith("/v1/performances") ||
                requestUri.startsWith("/actuator/");
    }
}