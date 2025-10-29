package org.ddcn41.queue.config;

import lombok.RequiredArgsConstructor;
import org.ddcn41.queue.security.JwtAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)  //  NOSONAR 쿠키의 SameSite가 Lax로 설정되어 CSRF 공격 차단
                .cors(AbstractHttpConfigurer::disable)  //  CORS 비활성화

                .authorizeHttpRequests(auth -> auth
                        // Swagger UI 허용
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // 공개 엔드포인트
                        .requestMatchers(
                                "/v1/queue/**",
                                "/v1/queue/status/*",
                                "/v1/queue/token/*/verify",
                                "/v1/queue/token/*/use",
                                "/v1/queue/release-session"
                        ).permitAll()  // 임시 모두 허용
                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
