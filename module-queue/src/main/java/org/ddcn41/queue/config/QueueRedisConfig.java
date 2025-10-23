package org.ddcn41.queue.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableRedisRepositories(basePackages = "org.ddcn41.queue.repository")
public class QueueRedisConfig {
}
