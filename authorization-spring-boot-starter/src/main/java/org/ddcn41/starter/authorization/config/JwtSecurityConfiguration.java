package org.ddcn41.starter.authorization.config;


import org.ddcn41.starter.authorization.filter.JwtAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(prefix = "jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class JwtSecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public JwtSecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                // CSRF 비활성화 (JWT 사용시)
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 사용 안함
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 기본 HTTP Basic 인증 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)

                // Form 로그인 비활성화
                .formLogin(AbstractHttpConfigurer::disable)

                // JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 기본 권한 설정
                .authorizeHttpRequests(authz -> authz
                        // 인증 관련 엔드포인트 허용
                        .requestMatchers("/v1/auth/**").permitAll()
                        .requestMatchers("/v1/admin/auth/login").permitAll()  // 관리자 로그인 허용
                        .requestMatchers("/v1/admin/auth/logout").permitAll()  // 관리자 로그아웃 허용

                        // 헬스체크 허용
                        .requestMatchers("/actuator/**").permitAll()

                        // Swagger / OpenAPI 문서 허용
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        .requestMatchers("/v1/admin/users/**").hasAnyRole("ADMIN", "DEVOPS")
                        .requestMatchers("/v1/admin/performances/**").hasAnyRole("ADMIN")
                        .requestMatchers("/v1/admin/schedules/**").hasRole("ADMIN")
                        .requestMatchers("/v1/admin/bookings/**").hasRole("ADMIN")

                        // internal API 허용
                        .requestMatchers("/v1/internal/**").permitAll()

                        // 공연조회 API 허용
                        .requestMatchers("/v1/performances/**").permitAll()

                        // 예매 관련 API - 인증 필요
                        .requestMatchers("/v1/bookings/**").authenticated()

                        // 좌석 조회 API 허용 (스케줄별 좌석 가용성 조회)
                        .requestMatchers("/v1/schedules/**").permitAll()

                        // 공연장 조회/좌석맵 조회 API (GET만 허용)
                        .requestMatchers(HttpMethod.GET, "/v1/venues/**").permitAll()

                        .requestMatchers(
                                "/v1/queue/**",
                                "/v1/queue/status/*",
                                "/v1/queue/token/*/verify",
                                "/v1/queue/token/*/use",
                                "/v1/queue/release-session"
                        ).permitAll()  // 임시 모두 허용

                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                );

                // 예외 처리
                http.exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                        .accessDeniedHandler(new JwtAccessDeniedHandler())
                );

        return http.build();
    }
}
