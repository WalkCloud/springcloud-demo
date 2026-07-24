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

### Kubernetes 部署

本项目目标运行环境为 Kubernetes。部署应用前，请确保集群中已存在可访问的 Nacos、Redis、MySQL 和 Kafka，并且以下 Service DNS 名称可用：

- `nacos:8848`
- `redis:6379`
- `mysql:3306`
- `kafka:9092`

创建命名空间和 MySQL 密码 Secret：

```bash
kubectl create namespace springcloud-demo
kubectl -n springcloud-demo create secret generic middleware-secret \
  --from-literal=mysql-password='<MySQL密码>'
```

将以下内容保存为 `springcloud-demo.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: provider-a
  namespace: springcloud-demo
spec:
  replicas: 2
  selector:
    matchLabels:
      app: provider-a
  template:
    metadata:
      labels:
        app: provider-a
    spec:
      containers:
        - name: provider-a
          image: kevinlee822/providera:latest
          imagePullPolicy: Always
          ports:
            - name: http
              containerPort: 8081
          env:
            - name: NACOS_SERVER_ADDR
              value: nacos:8848
            - name: NACOS_NAMESPACE
              value: public
            - name: ENABLE_MYSQL
              value: "true"
            - name: MYSQL_HOST
              value: mysql
            - name: MYSQL_PORT
              value: "3306"
            - name: MYSQL_USER
              value: root
            - name: MYSQL_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: middleware-secret
                  key: mysql-password
            - name: MYSQL_DATABASE
              value: provider_a_db
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 40
            periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: provider-a
  namespace: springcloud-demo
spec:
  selector:
    app: provider-a
  ports:
    - name: http
      port: 8081
      targetPort: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: provider-b
  namespace: springcloud-demo
spec:
  replicas: 2
  selector:
    matchLabels:
      app: provider-b
  template:
    metadata:
      labels:
        app: provider-b
    spec:
      containers:
        - name: provider-b
          image: kevinlee822/providerb:latest
          imagePullPolicy: Always
          ports:
            - name: http
              containerPort: 8082
          env:
            - name: NACOS_SERVER_ADDR
              value: nacos:8848
            - name: NACOS_NAMESPACE
              value: public
            - name: ENABLE_MYSQL
              value: "true"
            - name: MYSQL_HOST
              value: mysql
            - name: MYSQL_PORT
              value: "3306"
            - name: MYSQL_USER
              value: root
            - name: MYSQL_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: middleware-secret
                  key: mysql-password
            - name: MYSQL_DATABASE
              value: provider_b_db
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 40
            periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: provider-b
  namespace: springcloud-demo
spec:
  selector:
    app: provider-b
  ports:
    - name: http
      port: 8082
      targetPort: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: consumer
  namespace: springcloud-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: consumer
  template:
    metadata:
      labels:
        app: consumer
    spec:
      containers:
        - name: consumer
          image: kevinlee822/consumer:latest
          imagePullPolicy: Always
          ports:
            - name: http
              containerPort: 8080
          env:
            - name: NACOS_SERVER_ADDR
              value: nacos:8848
            - name: NACOS_NAMESPACE
              value: public
            - name: ENABLE_REDIS
              value: "true"
            - name: REDIS_HOST
              value: redis
            - name: REDIS_PORT
              value: "6379"
            - name: ENABLE_KAFKA
              value: "true"
            - name: KAFKA_SERVER
              value: kafka:9092
          readinessProbe:
            httpGet:
              path: /
              port: http
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /
              port: http
            initialDelaySeconds: 60
            periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: consumer
  namespace: springcloud-demo
spec:
  type: NodePort
  selector:
    app: consumer
  ports:
    - name: http
      port: 8080
      targetPort: http
      nodePort: 30080
```

部署并检查状态：

```bash
kubectl apply -f springcloud-demo.yaml
kubectl -n springcloud-demo get pods,svc
kubectl -n springcloud-demo rollout status deployment/provider-a
kubectl -n springcloud-demo rollout status deployment/provider-b
kubectl -n springcloud-demo rollout status deployment/consumer
```

通过任意 Kubernetes 节点的 `30080` 端口访问 Dashboard：

```text
http://<Node-IP>:30080/
```

也可以使用端口转发进行本地访问：

```bash
kubectl -n springcloud-demo port-forward service/consumer 8080:8080
```

然后访问 `http://localhost:8080/`。

弹性伸缩 Provider 副本：

```bash
kubectl -n springcloud-demo scale deployment/provider-a --replicas=3
kubectl -n springcloud-demo scale deployment/provider-b --replicas=3
```

如果 Nacos 开启了认证，请将用户名和密码保存为 Kubernetes Secret，并通过 `NACOS_USERNAME`、`NACOS_PASSWORD` 环境变量注入三个 Deployment；未开启认证时不要配置无效凭据。

停止并删除应用资源：

```bash
kubectl delete -f springcloud-demo.yaml
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

开启 `ENABLE_MYSQL=true` 后，需要先创建两个业务数据库。下面按“登录 → 建库 → 选库 → 建表 → 写入测试数据 → 查询验证”的顺序操作，适合不熟悉 MySQL 命令的用户。

#### 第 1 步：连接 MySQL

确认已经安装 MySQL 客户端，然后在终端执行：

```bash
mysql -h <MySQL地址> -P 3306 -u root -p
```

例如 MySQL 就在当前机器：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p
```

参数说明：

- `-h`：MySQL 地址。在 Kubernetes 中测试时，可以填写 MySQL Service 的外部地址或端口转发后的 `127.0.0.1`。
- `-P`：MySQL 端口，默认是 `3306`。这里是大写字母 `P`。
- `-u`：登录用户名，示例使用 `root`。
- `-p`：提示输入密码。执行命令后输入密码并按 Enter，输入过程中终端不会显示字符，这是正常现象。

连接成功后会看到类似提示：

```text
Welcome to the MySQL monitor.
mysql>
```

后面的 SQL 都在 `mysql>` 提示符后执行。每条 SQL 必须以英文分号 `;` 结尾。退出 MySQL 可执行：

```sql
EXIT;
```

如果出现 `mysql: command not found`，说明当前机器没有安装 MySQL 客户端；如果出现 `Access denied`，请检查用户名和密码；如果出现 `Can't connect`，请检查地址、端口、网络和 MySQL 服务状态。

#### 第 2 步：创建两个业务数据库

在 `mysql>` 提示符后逐条执行：

```sql
CREATE DATABASE IF NOT EXISTS provider_a_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS provider_b_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

查看数据库是否创建成功：

```sql
SHOW DATABASES;
```

结果中应包含：

```text
provider_a_db
provider_b_db
```

#### 第 3 步：进入 Provider A 数据库

MySQL 登录后默认不在任何业务数据库中。执行 `USE` 选择数据库：

```sql
USE provider_a_db;
```

成功后会显示：

```text
Database changed
```

查看当前选中的数据库：

```sql
SELECT DATABASE();
```

结果应为 `provider_a_db`。

#### 第 4 步：创建 Provider A 商品表

如果 Provider A 已成功启动，应用会自动执行 `providera/src/main/resources/schema.sql` 创建表和初始数据。初学者也可以手动执行以下 SQL：

```sql
CREATE TABLE IF NOT EXISTS product (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) DEFAULT '在售'
);
```

确认表已经创建：

```sql
SHOW TABLES;
DESCRIBE product;
```

插入一条测试商品。`ON DUPLICATE KEY UPDATE` 可以避免重复执行时报 SKU 冲突：

```sql
INSERT INTO product (sku, name, price, status)
VALUES ('DEMO-P-001', 'Demo 测试商品', 99.00, '在售')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  price = VALUES(price),
  status = VALUES(status);
```

查询并确认数据：

```sql
SELECT id, sku, name, price, status
FROM product
WHERE sku = 'DEMO-P-001';
```

#### 第 5 步：切换到 Provider B 数据库

仍然在同一个 MySQL 会话中执行：

```sql
USE provider_b_db;
SELECT DATABASE();
```

结果应为 `provider_b_db`。`USE` 就是“切换数据库”，不需要退出后重新登录。

#### 第 6 步：创建 Provider B 库存表

如果 Provider B 已成功启动，应用会自动执行 `providerb/src/main/resources/schema.sql` 创建表和初始数据。也可以手动执行：

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

确认表已经创建：

```sql
SHOW TABLES;
DESCRIBE inventory;
```

插入一条测试库存：

```sql
INSERT INTO inventory (sku, name, stock, warehouse, low_stock_threshold)
VALUES ('DEMO-SKU-001', 'Demo 测试库存', 600, 'wh-1', 200)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  stock = VALUES(stock),
  warehouse = VALUES(warehouse),
  low_stock_threshold = VALUES(low_stock_threshold);
```

查询并确认数据：

```sql
SELECT id, sku, name, stock, warehouse, low_stock_threshold,
       CASE WHEN stock < low_stock_threshold THEN '低库存' ELSE '正常' END AS status
FROM inventory
WHERE sku = 'DEMO-SKU-001';
```

#### 第 7 步：确认 Provider 使用的是 MySQL

确保 Provider A/B 的 Deployment 中已经设置：

```text
ENABLE_MYSQL=true
MYSQL_HOST=<MySQL地址或Service名称>
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=<密码>
MYSQL_DATABASE=provider_a_db 或 provider_b_db
```

重新部署或重启 Provider 后检查健康状态：

```bash
kubectl -n springcloud-demo rollout restart deployment/provider-a
kubectl -n springcloud-demo rollout restart deployment/provider-b
kubectl -n springcloud-demo rollout status deployment/provider-a
kubectl -n springcloud-demo rollout status deployment/provider-b
```

查看 Provider 日志，确认没有 MySQL 连接错误：

```bash
kubectl -n springcloud-demo logs deployment/provider-a --tail=100
kubectl -n springcloud-demo logs deployment/provider-b --tail=100
```

打开 Dashboard 后，Provider 返回数据中的 `dataSource` 应为 `mysql`。如果显示 `memory`，说明应用没有使用数据库，请重点检查 `ENABLE_MYSQL`、数据库地址、数据库名、账号、密码和网络连通性。

### 演示：修改 MySQL 数据，验证 Dashboard 实时刷新

完成上述步骤并确认 Provider 使用 MySQL 后，可以在同一个 MySQL 会话中修改数据。Dashboard 每 5 秒刷新一次。

#### 测试 Provider A 商品变化

先切换到商品数据库：

```sql
USE provider_a_db;
SELECT DATABASE();
```

新增或更新演示商品：

```sql
INSERT INTO product (sku, name, price, status)
VALUES ('DEMO-P-002', '云原生安全实战', 99.00, '热销')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  price = VALUES(price),
  status = VALUES(status);
```

确认写入成功：

```sql
SELECT * FROM product WHERE sku = 'DEMO-P-002';
```

修改价格：

```sql
UPDATE product
SET price = 199.00, status = '热销'
WHERE sku = 'DEMO-P-002';

SELECT * FROM product WHERE sku = 'DEMO-P-002';
```

#### 测试 Provider B 库存和低库存预警

切换到库存数据库：

```sql
USE provider_b_db;
SELECT DATABASE();
```

新增或更新演示库存：

```sql
INSERT INTO inventory (sku, name, stock, warehouse, low_stock_threshold)
VALUES ('DEMO-SKU-002', 'ArgoCD 实战', 600, 'wh-1', 200)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  stock = VALUES(stock),
  warehouse = VALUES(warehouse),
  low_stock_threshold = VALUES(low_stock_threshold);
```

确认当前状态是“正常”：

```sql
SELECT sku, name, stock, low_stock_threshold,
       CASE WHEN stock < low_stock_threshold THEN '低库存' ELSE '正常' END AS status
FROM inventory
WHERE sku = 'DEMO-SKU-002';
```

把库存修改到阈值以下：

```sql
UPDATE inventory
SET stock = 10
WHERE sku = 'DEMO-SKU-002';
```

再次查询，状态应变为“低库存”：

```sql
SELECT sku, name, stock, low_stock_threshold,
       CASE WHEN stock < low_stock_threshold THEN '低库存' ELSE '正常' END AS status
FROM inventory
WHERE sku = 'DEMO-SKU-002';
```

等待最多 5 秒后刷新 Dashboard，商品卡片、库存总量和低库存预警应反映最新数据。

#### 测试完成后清理演示数据

如果不希望保留测试记录，可以执行：

```sql
USE provider_a_db;
DELETE FROM product WHERE sku IN ('DEMO-P-001', 'DEMO-P-002');

USE provider_b_db;
DELETE FROM inventory WHERE sku IN ('DEMO-SKU-001', 'DEMO-SKU-002');
```

执行 `SELECT ROW_COUNT();` 可以查看上一条 `INSERT`、`UPDATE` 或 `DELETE` 影响了多少行。

## 示例效果

访问 `http://<consumer 所在主机 IP>:8080/` 即可看到 Dashboard：

- **集群概览**：3 个服务、N 个实例、全部在线
- **拓扑图**：直观展示浏览器 → Consumer → Provider 的调用链与 Nacos 注册关系
- **服务详情**：每个 provider 列出所有 Pod（多副本时每个 Pod 的 IP/状态都展示），provider-a 显示商品列表，provider-b 显示库存指标
- **弹性伸缩**：演示扩缩容 provider 副本，页面 5 秒内自动更新 Pod 数量与列表
- **中间件联动**：接 MySQL 后现场 INSERT/UPDATE，Dashboard 实时刷新数据（详见上文「中间件条件接入」）

## 访问关系

浏览器访问 consumer，consumer 通过 Nacos 注册中心发现并直接调用 provider-a、provider-b 的全部实例，在页面上聚合展示所有 Pod 信息。
