package com.demo.consumer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置。
 * 仅当 app.middleware.redis.enabled=true 时生效（ENABLE_REDIS=true）。
 * 关闭时不创建任何 Bean，CacheService 会自动降级为「不缓存」。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.middleware.redis", name = "enabled", havingValue = "true")
public class RedisConfig {

    @Value("${app.middleware.redis.host:localhost}")
    private String host;
    @Value("${app.middleware.redis.port:6379}")
    private int port;
    @Value("${app.middleware.redis.password:}")
    private String password;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isEmpty()) {
            cfg.setPassword(password);
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        factory.setValidateConnection(false);
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
