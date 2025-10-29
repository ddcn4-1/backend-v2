package org.ddcn41.starter.authorization.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.ddcn41.starter.authorization.properties.JwtProperties;
import org.ddcn41.starter.authorization.service.TokenBlacklistService;
import org.ddcn41.starter.authorization.validator.CognitoJwtValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "jwt.blacklist", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JwtBlacklistConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JwtBlacklistConfiguration.class);

    @Bean
    public RedisConnectionFactory jwtRedisConnectionFactory(JwtProperties jwtProperties) {
        JwtProperties.Blacklist.Redis redisProps = jwtProperties.getBlacklist().getRedis();

        // Redis Standalone Configuration
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProps.getHost());
        config.setPort(redisProps.getPort());
        config.setDatabase(redisProps.getDatabase());

        if (StringUtils.hasText(redisProps.getPassword())) {
            config.setPassword(redisProps.getPassword());
        }

        // Lettuce Client Configuration
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(redisProps.getConnectionTimeout()))
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redisProps.getCommandTimeout()))
                .clientOptions(clientOptions)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);

        logger.info("JWT Redis connection factory configured - Host: {}, Port: {}, DB: {}",
                redisProps.getHost(), redisProps.getPort(), redisProps.getDatabase());

        return factory;
    }

    @Bean(name = "jwtRedisTemplate")
    @ConditionalOnMissingBean(name = "jwtRedisTemplate")
    public RedisTemplate<String, String> jwtRedisTemplate(RedisConnectionFactory jwtRedisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(jwtRedisConnectionFactory);

        // String serializer 사용
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        logger.info("JWT Redis template configured successfully");

        return template;
    }

    @Bean
    public TokenBlacklistService tokenBlacklistService(
            RedisTemplate<String, String> jwtRedisTemplate,
            JwtProperties jwtProperties,
            CognitoJwtValidator jwtValidator) {

        return new TokenBlacklistService(jwtRedisTemplate, jwtProperties, jwtValidator);
    }
}
