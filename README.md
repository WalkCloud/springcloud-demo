# Spring Cloud Demo

灵雀云 Alauda 容器云平台 · 售前演示用的微服务 Demo。基于 Spring Cloud + Nacos 构建的微服务架构示例，前端为云平台控制台风格的实时 Dashboard，直观展示服务拓扑、多 Pod 实例、健康状态与弹性伸缩。

## 运行环境

```
OpenJDK 版本：eclipse-temurin 17
Spring Boot 版本：3.0.2
Spring Cloud 版本：2022.0.0
Spring Cloud For Alibaba 版本：2022.0.0.0
Nacos 版本：2.2.x ~ 2.4.x
容器编排：Kubernetes / Docker
```

## 技术栈

| 技术 | 角色 |
|------|------|
| OpenJDK 17 | 运行时 |
| Spring Boot 3.0 | 应用框架 |
| Spring Cloud | 微服务框架 |
| Spring Cloud Alibaba | 微服务组件（Nacos 注册/配置中心） |
| Kubernetes | 容器编排 |
| Thymeleaf | 模板引擎（仅 consumer 使用） |

## 架构

```
浏览器 → consumer (8080, 控制台 Dashboard)
              ↓ DiscoveryClient 从 Nacos 获取全部实例并逐一调用
         Nacos 注册中心 / 配置中心
              ↓
    ┌─────────┴──────────┐
 provider-a (8081)   provider-b (8082)
   商品服务            库存服务
```

- **consumer**：对外入口（端口 8080）。通过 `DiscoveryClient` 从 Nacos 获取 provider 的**全部实例**（多 Pod），逐一调用每个实例的 `/info`，从而在页面上展示全部 Pod 信息（而非负载均衡只显示一个）。前端为云平台控制台风格的实时 Dashboard。
- **provider-a**：模拟**商品服务**（端口 8081）。返回主机名、IP、星级评分 + 商品列表（SKU/名称/价格/状态）。
- **provider-b**：模拟**库存服务**（端口 8082）。返回主机名、IP、星级评分 + 库存汇总（SKU 总数/库存总量/仓库数/低库存预警）。
- 两个 provider 的星级（颜色/数量）通过 Nacos 配置中心动态下发，支持 `@RefreshScope` 热刷新。

### Consumer 接口

| 路径 | 说明 |
|------|------|
| `GET /` | 渲染主页面（Thymeleaf 模板，页面内 JS 轮询填充数据） |
| `GET /api/overview` | 返回聚合后的服务总览 JSON（consumer + 两个 provider 的全部实例 + 全局汇总），供前端 5 秒轮询刷新 |

## Dashboard 页面能力

页面纯原生 HTML + CSS + JS（不依赖任何外部 CDN，确保内网/离线演示可用），包含：

- **架构拓扑图**（SVG）：浏览器 → Consumer → Nacos → Provider A/B 的调用与注册关系，调用线带数据流动动画
- **集群概览 KPI**：服务总数 / 实例总数 / 在线实例 / 离线实例
- **技术栈卡片**：展示所用技术
- **服务详情卡片**：consumer / provider-a / provider-b 三张卡片，各展示副本数、每个 Pod 的实例 ID、IP:端口、健康状态徽标（UP/DOWN）、业务数据
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

## 示例效果

访问 `http://<consumer 所在主机 IP>:8080/` 即可看到 Dashboard：

- **集群概览**：3 个服务、N 个实例、全部在线
- **拓扑图**：直观展示浏览器 → Consumer → Provider 的调用链与 Nacos 注册关系
- **服务详情**：每个 provider 列出所有 Pod（多副本时每个 Pod 的 IP/状态都展示），provider-a 显示商品列表，provider-b 显示库存指标
- **实时刷新**：演示弹性伸缩时（扩容/缩容 provider 副本），页面 5 秒内自动更新 Pod 数量与列表

## 容器镜像

```
consumer 镜像：registry.cn-beijing.aliyuncs.com/walkcloud/consumer:latest
providera 镜像：registry.cn-beijing.aliyuncs.com/walkcloud/providera:latest
providerb 镜像：registry.cn-beijing.aliyuncs.com/walkcloud/providerb:latest
```

## 访问关系

浏览器访问 consumer，consumer 通过 Nacos 注册中心发现并直接调用 provider-a、provider-b 的全部实例，在页面上聚合展示所有 Pod 信息。
