-- provider-a 商品表（SKU 明细存储）
-- 应用启动时由 DataSourceConfig 在 ENABLE_MYSQL=true 时执行
CREATE TABLE IF NOT EXISTS product (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) DEFAULT '在售'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始商品数据（与硬编码保持一致，仅首次插入，重复 SKU 跳过）
INSERT IGNORE INTO product (sku, name, price, status) VALUES
  ('P-1001', '云原生实战手册', 89.00, '在售'),
  ('P-1002', '微服务架构指南', 128.00, '在售'),
  ('P-1003', 'Kubernetes 进阶', 156.00, '热销');
