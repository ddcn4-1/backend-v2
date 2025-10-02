package org.ddcn41.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "org.ddcn41.queue",
                "org.ddcn41.common"
        }
)
public class TicketingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingSystemApplication.class, args);
    }
}