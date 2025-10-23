package org.ddcn41.ticketing_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "org.ddcn41.ticketing_system"  // 이 패키지가 모든 모듈을 포함하는지 확인
})
@EnableJpaRepositories(
        basePackages = "org.ddcn41.ticketing_system"
)
@EntityScan(
        basePackages = "org.ddcn41.ticketing_system"
)
@EnableFeignClients(
        basePackages = "org.ddcn41.ticketing_system.common.client"
)
public class TicketingAdminApplication{
    public static void main(String[] args) {
        SpringApplication.run(TicketingAdminApplication.class, args);
    }
}

