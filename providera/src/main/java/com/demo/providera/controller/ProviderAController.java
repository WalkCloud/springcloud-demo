package com.demo.providera.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider A —— 模拟「商品服务」。
 * 在原有主机/IP/星级基础上，返回商品相关的业务数据，便于在售前 demo 中
 * 与 Provider B（库存服务）形成不同的业务场景区分。
 */
@RestController
@RefreshScope
public class ProviderAController {

    @Value("${star.color:gold}")  // 从 Nacos 配置中心读取
    private String starColor;

    @Value("${star.count:5}")     // 从 Nacos 配置中心读取
    private int starCount;

    /** 模拟商品数据（可由 Nacos 配置中心扩展）。 */
    private static final List<Map<String, Object>> PRODUCTS = Arrays.asList(
            product("P-1001", "云原生实战手册", 89.00, "在售"),
            product("P-1002", "微服务架构指南", 128.00, "在售"),
            product("P-1003", "Kubernetes 进阶", 156.00, "热销")
    );

    private static Map<String, Object> product(String sku, String name, double price, String status) {
        Map<String, Object> p = new HashMap<>();
        p.put("sku", sku);
        p.put("name", name);
        p.put("price", price);
        p.put("status", status);
        return p;
    }

    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            InetAddress host = InetAddress.getLocalHost();
            result.put("serviceName", "provider-a");
            result.put("displayName", "商品服务");
            result.put("businessType", "product");
            result.put("hostName", host.getHostName());
            result.put("ipAddress", host.getHostAddress());
            result.put("stars", Collections.nCopies(starCount, starColor));
            // 业务数据
            result.put("products", PRODUCTS);
            result.put("productCount", PRODUCTS.size());
            result.put("serviceSlogan", "提供商品目录与价格信息");
        } catch (Exception e) {
            result.put("error", "无法获取主机信息");
        }
        return result;
    }
}
