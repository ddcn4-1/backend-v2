package org.ddcn41.queue.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ddcn41.queue.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(AbstractHttpConfigurer::disable)  //  CORS 비활성화

                // 인증 실패 시 401 Unauthorized 반환
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"인증이 필요합니다\"}");
                        })
                )

                .authorizeHttpRequests(auth -> auth
                        // Swagger UI 허용
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // 공개 엔드포인트 (인증 불필요)
                        .requestMatchers(
                                "/v1/queue/status/*",
                                "/v1/queue/token/*/verify",
                                "/v1/queue/release-session"
                        ).permitAll()
                        // 인증이 필요한 엔드포인트
                        .requestMatchers(
                                "/v1/queue/check",
                                "/v1/queue/token",
                                "/v1/queue/activate",
                                "/v1/queue/my-tokens",
                                "/v1/queue/heartbeat",
                                "/v1/queue/token/*/use",
                                "/v1/queue/clear-sessions",
                                "/v1/queue/session-info"
                        ).authenticated()
                        // DELETE 메서드 (토큰 취소)
                        .requestMatchers("/v1/queue/token/*").authenticated()
                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
