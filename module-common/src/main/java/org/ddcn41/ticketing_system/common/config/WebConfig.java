package org.ddcn41.ticketing_system.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// ⭐ Profile을 'never'로 변경하여 실행되지 않도록 설정
@Deprecated(forRemoval = true)
@Configuration
@Profile("never")  // 이전: "dev"
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // empty
    }
}


@Deprecated(forRemoval = true)
@Configuration
@Profile("never")
class ProdWebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Nginx에서 처리하므로 불필요
    }
}