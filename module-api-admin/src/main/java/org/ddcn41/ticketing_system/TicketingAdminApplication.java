package org.ddcn41.ticketing_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "org.ddcn41.ticketing_system"  // 이 패키지가 모든 모듈을 포함하는지 확인
})
@EnableFeignClients(
        basePackages = "org.ddcn41.ticketing_system.common.client"
)
public class TicketingAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketingAdminApplication.class, args);
    }
}

