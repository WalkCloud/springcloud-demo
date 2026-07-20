package com.demo.providera.controller;

import com.demo.providera.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 数据来源：
 *   - ENABLE_MYSQL=true 且连通：从 MySQL product 表实时查询（可现场 INSERT 新数据，前端实时刷新）
 *   - 否则（开关关或连接失败）：回退到内存硬编码的固定商品数据
 */
@RestController
@RefreshScope
public class ProviderAController {

    @Value("${star.color:gold}")  // 从 Nacos 配置中心读取
    private String starColor;

    @Value("${star.count:5}")     // 从 Nacos 配置中心读取
    private int starCount;

    @Autowired
    private ProductRepository productRepository;

    /** 内存兜底商品数据（MySQL 未启用或不可达时使用） */
    private static final List<Map<String, Object>> FALLBACK_PRODUCTS = Arrays.asList(
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

            // 商品数据：优先 MySQL，回退内存
            List<Map<String, Object>> products = null;
            String dataSource = "memory";
            if (productRepository.isEnabled()) {
                products = productRepository.findAll();
                if (products != null) {
                    dataSource = "mysql";
                }
            }
            if (products == null) {
                products = FALLBACK_PRODUCTS;
                dataSource = "memory";
            }
            result.put("products", products);
            result.put("productCount", products.size());
            result.put("dataSource", dataSource);  // 标识数据来源，便于演示区分
            result.put("serviceSlogan", "提供商品目录与价格信息");
        } catch (Exception e) {
            result.put("error", "无法获取主机信息");
        }
        return result;
    }
}
