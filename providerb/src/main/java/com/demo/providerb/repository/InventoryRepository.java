package com.demo.providerb.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 库存数据访问（MySQL）。
 * 汇总指标（skuCount/totalStock/warehouseCount/lowStockCount）通过 SQL 聚合一次查出，
 * 体现 MySQL 的明细存储 + 聚合查询能力。
 *
 * JdbcTemplate 由 DataSourceConfig 在开关开启时注入；开关关闭时为 null，
 * 此时 getSummary() 返回 null，由 Controller 回退到内存硬编码数据。
 */
@Repository
public class InventoryRepository {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public boolean isEnabled() {
        return jdbcTemplate != null;
    }

    /**
     * 聚合查询库存汇总（单条 SQL 计算 4 个指标）。
     * @return 含 skuCount/totalStock/warehouseCount/lowStockCount 的 Map；
     *         MySQL 未启用或失败时返回 null（触发上层回退）。
     */
    public Map<String, Object> getSummary() {
        if (!isEnabled()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS sku_count, " +
                    "       COALESCE(SUM(stock),0) AS total_stock, " +
                    "       COUNT(DISTINCT warehouse) AS warehouse_count, " +
                    "       SUM(CASE WHEN stock < low_stock_threshold THEN 1 ELSE 0 END) AS low_stock_count " +
                    "FROM inventory");
        } catch (Exception e) {
            System.err.println("[provider-b] 查询 inventory 聚合失败，回退内存数据: " + e.getMessage());
            return null;
        }
    }
}
