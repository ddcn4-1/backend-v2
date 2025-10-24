package org.ddcn41.ticketing_system.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Ticketing System API",
                version = "0.0.1",
                description = "APIs for ticketing system"
        ),
        servers = {
                @Server(url = "https://local.api.ddcn41.com", description = "개발 서버 통합 Domain"),
                @Server(url = "http://localhost:8081", description = "Admin 개발 서버 localhost"),
                @Server(url = "http://localhost:8082", description = "Client 개발 서버 localhost"),
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT 토큰을 입력하세요"
)
public class SwaggerConfig {
}

