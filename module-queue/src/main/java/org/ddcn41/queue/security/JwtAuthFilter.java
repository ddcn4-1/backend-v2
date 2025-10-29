package org.ddcn41.queue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.queue.domain.CustomUserDetails;
import org.ddcn41.queue.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class JwtAuthFilter extends OncePerRequestFilter {

    private final CognitoJwtValidator cognitoValidator;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                CognitoJwtValidator.CognitoUserInfo userInfo =
                        cognitoValidator.validateAccessToken(token);

                if (userInfo != null && userInfo.getUsername() != null) {

                    String userId = userRepository
                            .findUserIdByUsername(userInfo.getUsername())
                            .map(String::valueOf)
                            .orElse(null);

                    if (userId == null) {
                        log.warn("⚠️ User not found in DB: {}", userInfo.getUsername());
                        filterChain.doFilter(request, response);
                        return;
                    }

                    setAuthentication(userInfo, userId, request);

                    log.debug("✅ Auth success - username: {}, userId: {}",
                            userInfo.getUsername(), userId);
                }

            } catch (Exception e) {
                log.error("❌ JWT auth error: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(
            CognitoJwtValidator.CognitoUserInfo userInfo,
            String userId,
            HttpServletRequest request
    ) {
        List<SimpleGrantedAuthority> authorities = userInfo.getGroups()
                .stream()
                .map(g -> new SimpleGrantedAuthority("ROLE_" + g.toUpperCase()))
                .collect(Collectors.toList());

        if (authorities.isEmpty()) {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        CustomUserDetails userDetails = new CustomUserDetails(
                userInfo.getUsername(),
                "",
                authorities,
                userId
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, authorities
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}