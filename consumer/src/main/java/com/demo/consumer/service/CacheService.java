package com.demo.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 缓存服务（缓存 /api/overview 的聚合结果）。
 * StringRedisTemplate 由 RedisConfig 在开关开启时注入；开关关闭时为 null，
 * 此时 getOverview() 返回 null、saveOverview() 静默跳过，主流程照常走聚合。
 *
 * 缓存值用 Jackson 序列化为 JSON 字符串存入 Redis。
 */
@Service
public class CacheService {

    private static final String KEY = "overview:json";
    private static final Duration TTL = Duration.ofSeconds(10);  // 与前端轮询周期匹配

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private StringRedisTemplate redis;

    /** 是否启用 Redis */
    public boolean isEnabled() {
        return redis != null;
    }

    /** 读取缓存并反序列化为 Map；未启用或异常返回 null（触发上层走聚合） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOverview() {
        if (!isEnabled()) return null;
        try {
            String json = redis.opsForValue().get(KEY);
            if (json == null) return null;
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            System.err.println("[consumer] Redis 读取失败，本次走聚合: " + e.getMessage());
            return null;
        }
    }

    /** 序列化后写入缓存；未启用或异常静默跳过 */
    public void saveOverview(Map<String, Object> data) {
        if (!isEnabled() || data == null) return;
        try {
            redis.opsForValue().set(KEY, mapper.writeValueAsString(data), TTL);
        } catch (Exception e) {
            System.err.println("[consumer] Redis 写入失败，跳过缓存: " + e.getMessage());
        }
    }
}
