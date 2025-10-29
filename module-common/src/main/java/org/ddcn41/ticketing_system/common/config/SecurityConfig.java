package org.ddcn41.ticketing_system.common.config;

import lombok.extern.slf4j.Slf4j;
import org.ddcn41.ticketing_system.common.authorization.filter.JwtAuthenticationFilter;
import org.ddcn41.ticketing_system.common.authorization.interfaces.CustomUserDetailsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Deprecated(forRemoval = true)
@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CognitoProperties.class)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public class SecurityConfig {

    private final CustomUserDetailsProvider userDetailsProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**"
    };

    public SecurityConfig(CustomUserDetailsProvider userDetailsProvider,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsProvider = userDetailsProvider;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService((UserDetailsService) userDetailsProvider);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // NOSONAR 쿠키의 SameSite가 Lax로 설정되어 CSRF 공격 차단
                //  CORS 비활성화 (Nginx에서 처리)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
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
                        .requestMatchers("/v1/bookings/**").permitAll()

                        // 좌석 조회 API 허용 (스케줄별 좌석 가용성 조회)
                        .requestMatchers("/v1/schedules/**").permitAll()

                        // 공연장 조회/좌석맵 조회 API (GET만 허용)
                        .requestMatchers(HttpMethod.GET, "/v1/venues/**").permitAll()

                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                );

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ⭐ corsConfigurationSource Bean 제거 또는 주석처리
    /*
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "https://ddcn41.com",
                "https://api.ddcn41.com",
                "https://local.ddcn41.com",
                "https://local.api.ddcn41.com",
                "https://local.accounts.ddcn41.com",
                "https://local.admin.ddcn41.com"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
        // Nginx에서 처리하므로 불필요
        ...
    }
    */
}