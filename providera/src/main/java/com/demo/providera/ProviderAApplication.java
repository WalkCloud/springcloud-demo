package com.demo.providera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ProviderAApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProviderAApplication.class, args);
        System.out.println("ProviderA 服务启动成功！");
    }
}