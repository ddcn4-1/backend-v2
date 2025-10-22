package org.ddcn41.queue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddcn41.queue.domain.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private volatile CognitoJwtValidatorLite cognito;

    @Value("${cognito.enabled:true}")
    private boolean cognitoEnabled;

    @Value("${cognito.issuer:}")
    private String issuer;

    @Value("${cognito.jwks:}")
    private String jwks;

    @Value("${cognito.appClientId:}")
    private String appClientId;

    private CognitoJwtValidatorLite validator() throws Exception {
        if (cognito == null) {
            synchronized (this) {
                if (cognito == null) {
                    cognito = new CognitoJwtValidatorLite(issuer, jwks, appClientId);
                }
            }
        }
        return cognito;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String token = extractToken(req);
        if (token != null && cognitoEnabled) {
            try {
                //  변경 : JJWT -> CognitoLite
                CognitoJwtValidatorLite.UserInfo info = validator().validateAccessToken(token);

                String username = info.getUsername();
                if (!StringUtils.hasText(username)) username = info.getSub();
                Long userId = info.getUserId(); // 커스텀 클레임 없으면 null

                if (userId == null) {
                    chain.doFilter(req, res);
                    return;
                }

                List<SimpleGrantedAuthority> authorities =
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

                CustomUserDetails userDetails = new CustomUserDetails(
                        username, "", authorities, userId
                );

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                log.error("JWT 인증 실패: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(req, res);
    }

    private String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}