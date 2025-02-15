package com.demo.providera.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RefreshScope
public class ProviderAController {

    @Value("${star.color:gold}")  // 从 Nacos 配置中心读取
    private String starColor;

    @Value("${star.count:5}")     // 从 Nacos 配置中心读取
    private int starCount;

    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            InetAddress host = InetAddress.getLocalHost();
            result.put("serviceName", "provider-a");
            result.put("hostName", host.getHostName());
            result.put("ipAddress", host.getHostAddress());
            result.put("stars", Collections.nCopies(starCount, starColor));
        } catch (Exception e) {
            result.put("error", "无法获取主机信息");
        }
        return result;
    }
}