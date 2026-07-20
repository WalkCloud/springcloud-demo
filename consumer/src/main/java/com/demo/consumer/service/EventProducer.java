package com.demo.consumer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 事件上报（每次访问 /api/overview 异步发一条事件消息）。
 * KafkaTemplate 由 KafkaConfig 在开关开启时注入；开关关闭时为 null，
 * 此时 sendAccessEvent() 静默跳过，绝不阻塞主流程。
 */
@Service
public class EventProducer {

    @Value("${app.middleware.kafka.topic:consumer-access-event}")
    private String topic;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafka;

    /** 是否启用 Kafka */
    public boolean isEnabled() {
        return kafka != null;
    }

    /** 异步发送访问事件；未启用或异常静默跳过 */
    public void sendAccessEvent(String message) {
        if (!isEnabled() || message == null) return;
        try {
            // 异步发送，不阻塞当前请求
            kafka.send(topic, message);
        } catch (Exception e) {
            System.err.println("[consumer] Kafka 发送失败，跳过事件上报: " + e.getMessage());
        }
    }
}
