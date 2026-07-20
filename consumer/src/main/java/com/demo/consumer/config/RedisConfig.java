package com.demo.consumer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis 配置。
 * 仅当 app.middleware.redis.enabled=true 时生效（ENABLE_REDIS=true）。
 * 关闭时不创建任何 Bean，CacheService 会自动降级为「不缓存」。
 *
 * 支持三种部署模式（由 app.middleware.redis.mode 控制）：
 *   - 不填/空 ：单机模式（host:port）
 *   - sentinel：哨兵模式（高可用主从 + 故障转移，通过 Sentinel 节点发现 master）
 *   - cluster ：集群模式（分片，多节点去中心化）
 */
@Configuration
@ConditionalOnProperty(prefix = "app.middleware.redis", name = "enabled", havingValue = "true")
public class RedisConfig {

    @Value("${app.middleware.redis.mode:}")
    private String mode;

    @Value("${app.middleware.redis.host:localhost}")
    private String host;
    @Value("${app.middleware.redis.port:6379}")
    private int port;
    @Value("${app.middleware.redis.password:}")
    private String password;
    @Value("${app.middleware.redis.database:0}")
    private int database;

    @Value("${app.middleware.redis.sentinel.master:mymaster}")
    private String sentinelMaster;
    @Value("${app.middleware.redis.sentinel.nodes:}")
    private String sentinelNodes;

    @Value("${app.middleware.redis.cluster.nodes:}")
    private String clusterNodes;

    /**
     * 根据部署模式构造对应的 RedisConfiguration。
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(buildConfiguration());
        factory.setValidateConnection(false);
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /** 解析逗号分隔的节点列表，如 "h1:26379, h2:26379" */
    private List<String> parseNodes(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** 设置密码到任一配置对象（三种具体类型都有 setPassword） */
    private void applyAuth(RedisStandaloneConfiguration cfg) {
        if (password != null && !password.isEmpty()) cfg.setPassword(password);
    }
    private void applyAuth(RedisSentinelConfiguration cfg) {
        if (password != null && !password.isEmpty()) cfg.setPassword(password);
    }
    private void applyAuth(RedisClusterConfiguration cfg) {
        if (password != null && !password.isEmpty()) cfg.setPassword(password);
    }

    private RedisConfiguration buildConfiguration() {
        String m = mode == null ? "" : mode.trim().toLowerCase();
        switch (m) {
            case "sentinel":
                // 哨兵模式：通过 Sentinel 节点列表发现 master，主挂自动切换
                RedisSentinelConfiguration sentinel = new RedisSentinelConfiguration();
                sentinel.master(sentinelMaster);
                for (String node : parseNodes(sentinelNodes)) {
                    String[] hp = node.split(":");
                    int p = hp.length > 1 ? Integer.parseInt(hp[1].trim()) : 26379;
                    sentinel.sentinel(hp[0].trim(), p);
                }
                if (database != 0) sentinel.setDatabase(database);
                applyAuth(sentinel);
                return sentinel;

            case "cluster":
                // 集群模式：多节点分片
                RedisClusterConfiguration cluster = new RedisClusterConfiguration(parseNodes(clusterNodes));
                applyAuth(cluster);
                return cluster;

            default:
                // 单机模式
                RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
                standalone.setDatabase(database);
                applyAuth(standalone);
                return standalone;
        }
    }
}
