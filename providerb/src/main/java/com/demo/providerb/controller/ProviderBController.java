package com.demo.providerb.controller;

import com.demo.providerb.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 数据来源：
 *   - ENABLE_MYSQL=true 且连通：从 MySQL inventory 表聚合查询（可现场 INSERT 新 SKU，前端实时刷新）
 *   - 否则（开关关或连接失败）：回退到内存硬编码的固定库存数据
 */
@RestController
@RefreshScope
public class ProviderBController {

    @Value("${star.color:gold}")
    private String starColor;

    @Value("${star.count:5}")
    private int starCount;

    @Autowired
    private InventoryRepository inventoryRepository;

    /** 内存兜底库存明细（MySQL 未启用或不可达时使用） */
    private static final java.util.List<java.util.Map<String, Object>> FALLBACK_INVENTORIES = java.util.Arrays.asList(
            inv("SKU-0001", "云原生实战手册", 3200, "wh-1", "正常"),
            inv("SKU-0002", "微服务架构指南", 4500, "wh-1", "正常"),
            inv("SKU-0003", "Kubernetes 进阶", 30, "wh-2", "低库存"),
            inv("SKU-0004", "DevOps 落地指南", 12000, "wh-1", "正常"),
            inv("SKU-0005", "Service Mesh 实战", 45, "wh-3", "低库存")
    );

    private static java.util.Map<String, Object> inv(String sku, String name, int stock, String warehouse, String status) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("sku", sku); m.put("name", name); m.put("stock", stock);
        m.put("warehouse", warehouse); m.put("status", status);
        return m;
    }

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

            // 库存汇总：优先 MySQL 聚合查询，回退内存
            int skuCount, totalStock, warehouseCount, lowStockCount;
            String dataSource;
            Map<String, Object> summary = null;
            if (inventoryRepository.isEnabled()) {
                summary = inventoryRepository.getSummary();
            }
            if (summary != null) {
                skuCount = toInt(summary.get("sku_count"));
                totalStock = toInt(summary.get("total_stock"));
                warehouseCount = toInt(summary.get("warehouse_count"));
                lowStockCount = toInt(summary.get("low_stock_count"));
                dataSource = "mysql";
            } else {
                skuCount = 1280;       // 内存兜底固定值
                totalStock = 56800;
                warehouseCount = 3;
                lowStockCount = 42;
                dataSource = "memory";
            }
            result.put("skuCount", skuCount);
            result.put("totalStock", totalStock);
            result.put("warehouseCount", warehouseCount);
            result.put("lowStockCount", lowStockCount);
            result.put("dataSource", dataSource);  // 标识数据来源

            // 库存明细列表：MySQL 启用时查全量 SKU 明细；否则用内存兜底
            java.util.List<java.util.Map<String, Object>> inventories = null;
            if (inventoryRepository.isEnabled()) {
                inventories = inventoryRepository.findAll();
            }
            if (inventories == null) {
                inventories = FALLBACK_INVENTORIES;
            }
            result.put("inventories", inventories);
            result.put("serviceSlogan", "提供实时库存与仓储信息");
        } catch (Exception e) {
            result.put("error", "无法获取主机信息");
        }
        return result;
    }

    /** 安全转 int（MySQL 聚合结果可能是 Long/BigDecimal） */
    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
