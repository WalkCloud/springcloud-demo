# Spring Cloud Demo

灵雀云 Alauda 容器云平台 · 售前演示用的微服务 Demo。基于 Spring Cloud + Nacos 构建的微服务架构示例，前端为云平台控制台风格的实时 Dashboard，直观展示服务拓扑、多 Pod 实例、健康状态与弹性伸缩。

## 运行环境

```
OpenJDK 版本：eclipse-temurin 21.0.11_10
Spring Boot 版本：3.2.5
Spring Cloud 版本：2023.0.1
Spring Cloud For Alibaba 版本：2023.0.1.0
Nacos 版本：2.2.x ~ 2.4.x
容器编排：Kubernetes / Docker
```

## 技术栈

| 技术 | 角色 |
|------|------|
| OpenJDK 21 | 运行时 |
| Spring Boot 3.2 | 应用框架 |
| Spring Cloud | 微服务框架 |
| Spring Cloud Alibaba | 微服务组件（Nacos 注册/配置中心） |
| Kubernetes | 容器编排 |
| Redis（可选） | 缓存（consumer 缓存聚合结果） |
| MySQL 8（可选） | 持久化（provider-a/b 业务数据） |
| Kafka（可选） | 消息队列（consumer 异步上报访问事件） |
| Thymeleaf | 模板引擎（仅 consumer 使用） |

## 架构

```
浏览器 → consumer (8080, 控制台 Dashboard)
              ├─ Redis   (缓存聚合结果，ENABLE_REDIS=true 时启用)
              ├─ Kafka   (异步上报访问事件，ENABLE_KAFKA=true 时启用)
              ↓ DiscoveryClient 从 Nacos 获取全部实例并逐一调用
         Nacos 注册中心 / 配置中心
              ↓
    ┌─────────┴──────────┐
 provider-a (8081)   provider-b (8082)
   商品服务            库存服务
   └─ MySQL ──┐  └─ MySQL ──┘  (ENABLE_MYSQL=true 时启用，各自独立库)
```

- **consumer**：对外入口（端口 8080）。通过 `DiscoveryClient` 从 Nacos 获取 provider 的**全部实例**（多 Pod），逐一调用每个实例的 `/info`，从而在页面上展示全部 Pod 信息（而非负载均衡只显示一个）。前端为云平台控制台风格的实时 Dashboard。
- **provider-a**：模拟**商品服务**（端口 8081）。返回主机名、IP、星级评分 + 商品列表（SKU/名称/价格/状态）。
- **provider-b**：模拟**库存服务**（端口 8082）。返回主机名、IP、星级评分 + 库存汇总（SKU 总数/库存总量/仓库数/低库存预警）和库存明细。
- 两个 provider 的星级（颜色/数量）通过 Nacos 配置中心动态下发，支持 `@RefreshScope` 热刷新。
- **可选中间件**（Redis/MySQL/Kafka）通过开关控制，默认关闭，详见下文「中间件条件接入」。

### Consumer 接口

| 路径 | 说明 |
|------|------|
| `GET /` | 渲染主页面（Thymeleaf 模板，页面内 JS 轮询填充数据） |
| `GET /api/overview` | 返回聚合后的服务总览 JSON（consumer + 两个 provider 的全部实例 + 全局汇总），供前端 5 秒轮询刷新 |

## Dashboard 页面能力

页面纯原生 HTML + CSS + JS（不依赖任何外部 CDN，确保内网/离线演示可用），包含：

- **架构拓扑图**（SVG）：浏览器 → Consumer → Nacos → Provider A/B 的调用与注册关系，调用线带数据流动动画
- **集群概览 KPI**：服务总数 / 实例总数 / 在线实例 / 离线实例
- **技术栈卡片**：展示基础技术，并根据 `/api/overview` 动态显示已启用的 Redis、MySQL 和 Kafka
- **服务详情卡片**：consumer / provider-a / provider-b 三张卡片，各展示副本数、每个 Pod 的实例 ID、IP:端口、健康状态徽标（UP/DOWN）、业务数据；Consumer 卡片同时展示 Redis 缓存与 Kafka 事件上报状态
- **Nacos 注册表**：三列展示（服务名 / 实例数 / 实例 IP），实时同步自 Nacos
- **实时刷新**：每 5 秒轮询 `/api/overview`，弹性伸缩（Pod 增减）时页面实时变化
- **品牌标识**：灵雀云 Alauda 品牌露出

## 构建与运行

每个服务独立构建（无父 POM，各自是独立的 Spring Boot 应用）：

```bash
# 构建某个服务（在服务目录下执行）
./mvnw clean package

# 本地运行（需先启动 Nacos）
java -jar target/<artifact>-1.0.jar
```

Docker 构建（在服务目录下）：

```bash
docker build -t <service-name> .
```

### Docker Hub 镜像

已发布的 `linux/amd64` 演示镜像：

| 服务 | 镜像 |
|------|------|
| Consumer | `kevinlee822/consumer:latest` |
| Provider A | `kevinlee822/providera:latest` |
| Provider B | `kevinlee822/providerb:latest` |

```bash
docker pull kevinlee822/consumer:latest
docker pull kevinlee822/providera:latest
docker pull kevinlee822/providerb:latest
```

本项目当前没有 Docker Compose 文件。使用 Docker 演示时，需要先创建网络并启动 Nacos、Redis、MySQL、Kafka，再通过 `docker run` 启动各应用容器。下面是与本 Demo 一致的应用容器示例，基础设施容器需已加入 `demonet` 网络：

```bash
docker network create demonet 2>/dev/null || true

# Provider A，可用不同容器名重复执行以模拟多副本
docker run -d --name providera1 --network demonet \
  -e NACOS_SERVER_ADDR=nacos:8848 \
  -e NACOS_NAMESPACE=public \
  -e ENABLE_MYSQL=true \
  -e MYSQL_HOST=mysql \
  -e MYSQL_PORT=3306 \
  -e MYSQL_USER=root \
  -e MYSQL_PASSWORD=<密码> \
  -e MYSQL_DATABASE=provider_a_db \
  kevinlee822/providera:latest

# Provider B，可用不同容器名重复执行以模拟多副本
docker run -d --name providerb1 --network demonet \
  -e NACOS_SERVER_ADDR=nacos:8848 \
  -e NACOS_NAMESPACE=public \
  -e ENABLE_MYSQL=true \
  -e MYSQL_HOST=mysql \
  -e MYSQL_PORT=3306 \
  -e MYSQL_USER=root \
  -e MYSQL_PASSWORD=<密码> \
  -e MYSQL_DATABASE=provider_b_db \
  kevinlee822/providerb:latest

# Consumer
docker run -d --name consumer --network demonet -p 8080:8080 \
  -e NACOS_SERVER_ADDR=nacos:8848 \
  -e NACOS_NAMESPACE=public \
  -e ENABLE_REDIS=true \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e ENABLE_KAFKA=true \
  -e KAFKA_SERVER=kafka:9092 \
  kevinlee822/consumer:latest
```

如果 Nacos 已开启认证，再为三个应用容器增加 `NACOS_USERNAME` 和 `NACOS_PASSWORD`；未开启认证时不要传入无效凭据。

停止 Demo 容器：

```bash
docker stop consumer providera1 providerb1 nacos redis mysql kafka
```

## 必备基础设施

- **Nacos 2.2.x ~ 2.4.x** 必须先于任何服务启动
- 三个服务均通过环境变量配置 Nacos 连接：
  - `NACOS_SERVER_ADDR`：Nacos 访问地址，如 `192.168.0.1:8848`
  - `NACOS_NAMESPACE`：命名空间，如 `public`
  - `NACOS_USERNAME`：用户名，如 `nacos`
  - `NACOS_PASSWORD`：密码，如 `nacos`

## Nacos 配置中心

provider-a / provider-b 通过 `spring.config.import` 从 Nacos 拉取星级配置，支持动态刷新。

provider-a 的 `application.yml`：
```yaml
spring:
  config:
    import: "optional:nacos:provider-a.yml"
```

Nacos 配置中心中 `dataId = provider-a.yml`（YAML 格式）的内容：
```yaml
star:
  color: blue   # red/blue/green/yellow/gold/silver/orange/purple/pink/black/white
  count: 5      # 星星数量
```

> `application.yml` 中的 `import` 值必须与 Nacos 配置中心的 Data ID 一致，配置才能下发到应用。provider-b 同理（`provider-b.yml`）。

## 中间件条件接入（Redis / MySQL / Kafka）

三个中间件通过**环境变量开关**控制是否启用，默认全部关闭。不启用时 demo 行为与纯微服务完全一致；启用后连不上则自动降级，绝不中断。

| 中间件 | 接入服务 | 用途 | 启用开关 |
|--------|---------|------|---------|
| Redis | consumer | 缓存 `/api/overview` 聚合结果（TTL 10s） | `ENABLE_REDIS=true` |
| MySQL | provider-a / provider-b | 业务数据持久化（按 SKU 明细存储） | `ENABLE_MYSQL=true` |
| Kafka | consumer | 异步上报访问事件到 topic `consumer-access-event` | `ENABLE_KAFKA=true` |

### 设计原则

- **开关控制**：开关关 → 对应 Bean 不创建、不连接（用 `@ConditionalOnProperty` + `autoconfigure.exclude` 双保险），demo 与改造前行为 100% 一致。
- **运行时降级**：开关开但连不上 → try-catch 回退到内存默认数据，demo 继续可用。
- **接口零变化**：`/info` 返回结构前后一致，Dashboard 前端无需改动。provider 返回额外带 `dataSource` 字段（`"mysql"` 或 `"memory"`）标识数据来源，便于演示区分。

### 数据来源对照

| 场景 | provider 数据来源 | 演示价值 |
|------|-----------------|---------|
| 不接 MySQL | 内存硬编码（固定） | 基础展示，数据写死、改不了 |
| 接 MySQL | 数据库实时查询 | **现场往库里 INSERT 新数据，Dashboard 5 秒内实时刷新显示** ← 核心演示卖点 |

### 环境变量

```bash
# Consumer（Redis + Kafka，按需开启）
ENABLE_REDIS=true
REDIS_HOST=<redis-host>
REDIS_PORT=6379
REDIS_PASSWORD=<可选>

ENABLE_KAFKA=true
KAFKA_SERVER=<kafka-host>:9092

# Provider-a（MySQL，库 provider_a_db）
ENABLE_MYSQL=true
MYSQL_HOST=<mysql-host>
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=<密码>
MYSQL_DATABASE=provider_a_db

# Provider-b（MySQL，库 provider_b_db）
ENABLE_MYSQL=true
MYSQL_HOST=<mysql-host>
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=<密码>
MYSQL_DATABASE=provider_b_db
```

> 不设置任何 `ENABLE_XXX` 即为纯微服务模式，无需 Redis/MySQL/Kafka。

### MySQL 登录、建库与建表

开启 `ENABLE_MYSQL=true` 后，需要先创建两个业务数据库。Provider 首次启动会自动执行各自的 `schema.sql` 创建表并灌入初始数据。

使用 MySQL 客户端登录：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p
```

根据提示输入密码，进入 MySQL 后创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS provider_a_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS provider_b_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

SHOW DATABASES;
```

Provider 启动后验证表和数据：

```sql
USE provider_a_db;
SHOW TABLES;
DESCRIBE product;
SELECT * FROM product ORDER BY id;

USE provider_b_db;
SHOW TABLES;
DESCRIBE inventory;
SELECT * FROM inventory ORDER BY id;
```

如果需要手动建表，可执行以下 SQL。

**provider-a `product` 表**（商品 SKU 明细）：

```sql
CREATE TABLE IF NOT EXISTS product (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) DEFAULT '在售'
);
```

**provider-b `inventory` 表**（库存 SKU 明细，汇总值用 SQL 聚合算出）：

```sql
CREATE TABLE IF NOT EXISTS inventory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(128),
  stock INT NOT NULL DEFAULT 0,
  warehouse VARCHAR(32) DEFAULT 'wh-1',
  low_stock_threshold INT DEFAULT 50
);
```

### 演示：现场修改数据，Dashboard 实时刷新

接上 MySQL 并 `ENABLE_MYSQL=true` 启动 provider 后，用 SQL 客户端连库执行以下命令，Dashboard 会在下一次轮询（≤5 秒）实时展示变化。

**provider-a 新增一个商品**：
```sql
USE provider_a_db;
INSERT INTO product (sku, name, price, status) VALUES ('P-1004', '云原生安全实战', 99.00, '热销');
```

**provider-a 修改商品价格**：
```sql
USE provider_a_db;
UPDATE product SET price = 199.00, status = '热销' WHERE sku = 'P-1001';
```

**provider-a 删除商品**：
```sql
USE provider_a_db;
DELETE FROM product WHERE sku = 'P-1002';
```

**provider-b 新增一个 SKU 库存**（库存汇总指标会随之自动重新聚合）：
```sql
USE provider_b_db;
INSERT INTO inventory (sku, name, stock, warehouse, low_stock_threshold)
VALUES ('SKU-0009', 'ArgoCD 实战', 600, 'wh-1', 200);
```

**provider-b 调整某 SKU 库存量**（演示低库存预警变化）：
```sql
USE provider_b_db;
UPDATE inventory SET stock = 10 WHERE sku = 'SKU-0001';  -- 低于阈值，low_stock_count 会 +1
```

执行后刷新 consumer 页面（http://<consumer-ip>:8080/），商品卡片/库存指标会实时反映库里的最新数据。provider 返回的 `dataSource` 字段为 `"mysql"` 即表示数据来自数据库。

## 示例效果

访问 `http://<consumer 所在主机 IP>:8080/` 即可看到 Dashboard：

- **集群概览**：3 个服务、N 个实例、全部在线
- **拓扑图**：直观展示浏览器 → Consumer → Provider 的调用链与 Nacos 注册关系
- **服务详情**：每个 provider 列出所有 Pod（多副本时每个 Pod 的 IP/状态都展示），provider-a 显示商品列表，provider-b 显示库存指标
- **弹性伸缩**：演示扩缩容 provider 副本，页面 5 秒内自动更新 Pod 数量与列表
- **中间件联动**：接 MySQL 后现场 INSERT/UPDATE，Dashboard 实时刷新数据（详见上文「中间件条件接入」）

## 访问关系

浏览器访问 consumer，consumer 通过 Nacos 注册中心发现并直接调用 provider-a、provider-b 的全部实例，在页面上聚合展示所有 Pod 信息。
