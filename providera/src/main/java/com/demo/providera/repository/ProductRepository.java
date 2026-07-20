package com.demo.providera.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品数据访问（MySQL）。
 * JdbcTemplate 由 DataSourceConfig 在开关开启时注入；开关关闭时为 null，
 * 此时 findAll() 返回 null，由 Controller 回退到内存硬编码数据。
 */
@Repository
public class ProductRepository {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /** 是否启用 MySQL（JdbcTemplate 存在即视为启用） */
    public boolean isEnabled() {
        return jdbcTemplate != null;
    }

    /**
     * 查询全部商品（SKU 明细）。
     * @return 商品列表；MySQL 未启用或查询失败时返回 null（触发上层回退）。
     */
    public List<Map<String, Object>> findAll() {
        if (!isEnabled()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForList(
                    "SELECT sku, name, price, status FROM product ORDER BY id");
        } catch (Exception e) {
            System.err.println("[provider-a] 查询 product 表失败，回退内存数据: " + e.getMessage());
            return null;
        }
    }

    /** 商品数量（聚合统计） */
    public int count() {
        if (!isEnabled()) {
            return 0;
        }
        try {
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class);
            return cnt == null ? 0 : cnt;
        } catch (Exception e) {
            return 0;
        }
    }
}
