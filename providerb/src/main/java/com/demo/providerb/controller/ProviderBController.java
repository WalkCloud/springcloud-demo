package com.demo.providerb.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Provider B —— 模拟「库存服务」。
 * 在原有主机/IP/星级基础上，返回库存相关的业务数据，便于在售前 demo 中
 * 与 Provider A（商品服务）形成不同的业务场景区分。
 */
@RestController
@RefreshScope
public class ProviderBController {

    @Value("${star.color:gold}")
    private String starColor;

    @Value("${star.count:5}")
    private int starCount;

    @GetMapping("/info")
    public Map<String, Object> getInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            InetAddress host = InetAddress.getLocalHost();
            result.put("serviceName", "provider-b");
            result.put("displayName", "库存服务");
            result.put("businessType", "inventory");
            result.put("hostName", host.getHostName());
            result.put("ipAddress", host.getHostAddress());
            result.put("stars", Collections.nCopies(starCount, starColor));
            // 业务数据：库存汇总
            result.put("skuCount", 1280);
            result.put("totalStock", 56800);
            result.put("warehouseCount", 3);
            result.put("lowStockCount", 42);
            result.put("serviceSlogan", "提供实时库存与仓储信息");
        } catch (Exception e) {
            result.put("error", "无法获取主机信息");
        }
        return result;
    }
}
