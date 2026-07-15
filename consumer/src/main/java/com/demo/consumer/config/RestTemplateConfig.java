package com.demo.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 使用普通 RestTemplate 直接调用具体的 ip:port，
        // 具体实例由 DiscoveryClient 在 Controller 中获取，从而展示所有 pod。
        return new RestTemplate();
    }
}
