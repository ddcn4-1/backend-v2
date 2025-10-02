package org.ddcn41.ticketing_system.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ddcn41.ticketing_system.common.service.CustomUserDetailsProvider;
import org.ddcn41.ticketing_system.common.service.JwtTokenValidator;
import org.ddcn41.ticketing_system.common.service.TokenBlacklistChecker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator jwtTokenValidator;
    private final CustomUserDetailsProvider userDetailsProvider;
    private final TokenBlacklistChecker tokenBlacklistChecker;

    public JwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator,
                                   CustomUserDetailsProvider userDetailsProvider,
                                   TokenBlacklistChecker tokenBlacklistChecker) {
        this.jwtTokenValidator = jwtTokenValidator;
        this.userDetailsProvider = userDetailsProvider;
        this.tokenBlacklistChecker = tokenBlacklistChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // 1. 블랙리스트 체크
                if (tokenBlacklistChecker.isTokenBlacklisted(token)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Token has been invalidated\"}");
                    return;
                }

                // 2. 토큰에서 사용자명 추출
                String username = jwtTokenValidator.extractUsername(token);

                // 3. 토큰 유효성 검증
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsProvider.loadUserByUsername(username);

                    if (jwtTokenValidator.validateToken(token, username)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception e) {
                // 토큰이 유효하지 않은 경우
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid token\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}