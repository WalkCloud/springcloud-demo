-- provider-b 库存表（按 SKU 明细存储，分布多个仓库）
-- 应用启动时由 DataSourceConfig 在 ENABLE_MYSQL=true 时执行
CREATE TABLE IF NOT EXISTS inventory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(128),
  stock INT NOT NULL DEFAULT 0,
  warehouse VARCHAR(32) DEFAULT 'wh-1',
  low_stock_threshold INT DEFAULT 50
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始库存明细（约 8 条，分布 3 个仓库，含低库存样本）
-- 汇总值 sku_count/total_stock/warehouse_count/low_stock_count 由 SQL 聚合算出
INSERT IGNORE INTO inventory (sku, name, stock, warehouse, low_stock_threshold) VALUES
  ('SKU-0001', '云原生实战手册',   3200, 'wh-1', 200),
  ('SKU-0002', '微服务架构指南',   4500, 'wh-1', 200),
  ('SKU-0003', 'Kubernetes 进阶',  1800, 'wh-2', 200),
  ('SKU-0004', 'Docker 实战',       30, 'wh-2', 200),
  ('SKU-0005', 'DevOps 落地指南', 12000, 'wh-1', 500),
  ('SKU-0006', 'Service Mesh 实战',9000, 'wh-3', 500),
  ('SKU-0007', 'Prometheus 监控',   45, 'wh-3', 200),
  ('SKU-0008', 'Helm 部署手册',    22725, 'wh-2', 1000);
