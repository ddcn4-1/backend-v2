// src/test/java/org/ddcn41/queue/config/EmbeddedRedisConfig.java

package org.ddcn41.queue.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

@Slf4j
@TestConfiguration
@Profile("test")
public class EmbeddedRedisConfig {

    private RedisServer redisServer;
    private final int port = 6370; // 테스트용 포트

    @PostConstruct
    public void startRedis() throws IOException {
        try {
            redisServer = RedisServer.builder()
                    .port(port)
                    .setting("maxmemory 128M")
                    .build();
            redisServer.start();
            log.info("✅ Embedded Redis started on port: {}", port);
        } catch (Exception e) {
            log.error("❌ Failed to start Embedded Redis", e);
            throw e;
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
            log.info("🛑 Embedded Redis stopped");
        }
    }
}