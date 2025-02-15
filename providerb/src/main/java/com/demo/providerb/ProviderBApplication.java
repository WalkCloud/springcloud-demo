package com.demo.providerb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ProviderBApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProviderBApplication.class, args);
        System.out.println("ProviderA 服务启动成功！");
    }
}