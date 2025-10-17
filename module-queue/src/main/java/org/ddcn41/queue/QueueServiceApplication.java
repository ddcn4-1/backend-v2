package org.ddcn41.queue;

import org.ddcn41.ticketing_system.common.config.CognitoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "org.ddcn41.queue",
                "org.ddcn41.ticketing_system.auth.utils",      // module-auth의 JwtUtil만
                "org.ddcn41.ticketing_system.common.service",  // module-common의 CognitoJwtValidator
                "org.ddcn41.ticketing_system.common.config"    // module-common의 설정
        },
        excludeFilters = {
                // module-common의 SecurityConfig 제외 (module-queue의 QueueSecurityConfig 사용)
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                org.ddcn41.ticketing_system.common.config.SecurityConfig.class,
                                org.ddcn41.ticketing_system.common.config.JwtAuthenticationFilter.class
                        }
                )
        }
)
@EnableConfigurationProperties(CognitoProperties.class)  // CognitoProperties 활성화
@EnableFeignClients
@EnableScheduling
@EnableJpaRepositories(basePackages = "org.ddcn41.queue.repository")
@EntityScan(basePackages = "org.ddcn41.queue.entity")
public class QueueServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueueServiceApplication.class, args);
    }
}