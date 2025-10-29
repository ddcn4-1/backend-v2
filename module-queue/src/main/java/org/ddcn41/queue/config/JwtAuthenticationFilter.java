// module-queue/src/main/java/org/ddcn41/queue/config/JwtAuthenticationFilter.java

package org.ddcn41.queue.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.queue.domain.CustomUserDetails;
import org.ddcn41.queue.repository.UserRepository;
import org.ddcn41.queue.security.CognitoJwtValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final CognitoJwtValidator cognitoValidator;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        log.debug("=== JWT Filter Start ===");
        log.debug("Request URI: {}", request.getRequestURI());

        //  1. Authorization 헤더에서 토큰 찾기
        String token = extractTokenFromHeader(request);

        //  2. 헤더에 없으면 쿠키에서 찾기
        if (token == null) {
            token = extractTokenFromCookie(request);
            log.debug("Token from cookie: {}", token != null);
        } else {
            log.debug("Token from header: present");
        }

        if (token != null) {
            try {
                log.debug("Validating token...");

                // 3. Cognito JWT 검증
                CognitoJwtValidator.CognitoUserInfo userInfo =
                        cognitoValidator.validateAccessToken(token);

                if (userInfo != null && userInfo.getUsername() != null) {
                    log.debug("✅ Token valid - username: {}", userInfo.getUsername());

                    //  4. username → userId 변환 (DB 조회)
                    String userId = userRepository
                            .findUserIdByUsername(userInfo.getUsername())
                            .map(String::valueOf)
                            .orElse(null);

                    if (userId == null) {
                        log.warn("⚠️ User not found in DB: {}", userInfo.getUsername());
                        filterChain.doFilter(request, response);
                        return;
                    }

                    log.debug("✅ Found userId: {}", userId);

                    //  5. 권한 설정
                    List<SimpleGrantedAuthority> authorities = userInfo.getGroups()
                            .stream()
                            .map(g -> new SimpleGrantedAuthority("ROLE_" + g.toUpperCase()))
                            .collect(Collectors.toList());

                    if (authorities.isEmpty()) {
                        authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    }

                    log.debug("Authorities: {}", authorities);

                    // 6. CustomUserDetails 생성
                    CustomUserDetails userDetails = new CustomUserDetails(
                            userInfo.getUsername(),
                            "",
                            authorities,
                            userId  // String 타입
                    );

                    //  7. Spring Security Context 설정
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, authorities
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("✅ Authentication set successfully");
                } else {
                    log.warn("⚠️ Token validation returned null");
                }

            } catch (Exception e) {
                log.error("❌ JWT auth error: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else {
            log.warn("⚠️ No token found (neither header nor cookie)");
        }

        filterChain.doFilter(request, response);
    }

    /**
     *  Authorization 헤더에서 토큰 추출
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     *  쿠키에서 access_token 추출
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> "access_token".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}