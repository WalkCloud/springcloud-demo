package com.demo.consumer.controller;

import com.demo.consumer.service.CacheService;
import com.demo.consumer.service.EventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ConsumerController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private EventProducer eventProducer;

    @Value("${server.port:8080}")
    private String serverPort;

    /** 渲染主页面（页面内 JS 轮询 /api/overview 填充数据）。 */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * 返回聚合后的服务总览 JSON，供前端轮询刷新。
     * 链路：查 Redis 缓存 → 命中直接返回 → 未命中走聚合 → 写缓存 + 发 Kafka 事件。
     * 缓存/Kafka 未启用或异常都不影响主流程（自动降级）。
     */
    @GetMapping("/api/overview")
    @ResponseBody
    public Map<String, Object> overview() {
        // 1. 先查 Redis 缓存
        Map<String, Object> cached = cacheService.getOverview();
        if (cached != null) {
            cached.put("cached", true);  // 标识命中缓存
            return cached;
        }

        // 2. 未命中，走聚合
        Map<String, Object> root = buildOverview();

        // 3. 写缓存（异步、失败静默）+ 发 Kafka 访问事件
        cacheService.saveOverview(root);
        eventProducer.sendAccessEvent("{\"type\":\"overview-access\",\"timestamp\":" + System.currentTimeMillis()
                + ",\"host\":\"" + safeLocalHostName() + "\"}");
        root.put("cached", false);
        return root;
    }

    /** 构建聚合 overview（原聚合逻辑，从 Redis 缓存缺失时调用） */
    private Map<String, Object> buildOverview() {
        Map<String, Object> root = new LinkedHashMap<>();

        // ---- consumer 自身聚合数据（含副本数 + 实例列表）----
        Map<String, Object> consumer = buildConsumerOverview();
        root.put("consumer", consumer);

        // ---- 两个 provider 的聚合数据 ----
        Map<String, Object> providerA = buildServiceOverview("provider-a", "Service Provider A");
        Map<String, Object> providerB = buildServiceOverview("provider-b", "Service Provider B");
        root.put("providerA", providerA);
        root.put("providerB", providerB);

        // ---- 全局汇总（基于实际注册中心统计，不再硬编码）----
        int totalServices = 0;
        int totalInstances = 0;
        int onlineInstances = 0;
        int offlineInstances = 0;
        for (Map<String, Object> p : new Map[]{consumer, providerA, providerB}) {
            int t = (int) p.get("total");
            int on = (int) p.get("online");
            if (t > 0) {
                totalServices++;
            }
            totalInstances += t;
            onlineInstances += on;
            offlineInstances += (t - on);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalServices", totalServices);
        summary.put("totalInstances", totalInstances);
        summary.put("onlineInstances", onlineInstances);
        summary.put("offlineInstances", offlineInstances);
        root.put("summary", summary);

        root.put("timestamp", System.currentTimeMillis());
        return root;
    }

    /**
     * 聚合 consumer 自身的全部实例（pod）信息。
     * consumer 作为对外入口，其副本数（pod 数量）同样需要展示，
     * 以便在弹性伸缩时直观看到 consumer 的扩缩容。
     */
    private Map<String, Object> buildConsumerOverview() {
        Map<String, Object> svc = new LinkedHashMap<>();
        svc.put("serviceName", "consumer");
        svc.put("displayName", "Consumer 服务");
        svc.put("port", serverPort);

        List<ServiceInstance> instances = discoveryClient.getInstances("consumer");
        int total = instances == null ? 0 : instances.size();
        svc.put("total", total);
        svc.put("online", total);   // 能注册到注册中心即视为在线
        svc.put("offline", 0);

        List<Map<String, Object>> instanceList = new ArrayList<>();
        if (instances != null) {
            for (ServiceInstance instance : instances) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("instanceId", instance.getInstanceId());
                row.put("scheme", instance.getScheme());
                row.put("port", instance.getPort());
                row.put("uri", String.valueOf(instance.getUri()));
                row.put("ipAddress", instance.getHost());
                row.put("status", "UP");
                instanceList.add(row);
            }
        }
        svc.put("instances", instanceList);

        // 本机信息（footer 展示用）
        svc.put("local", selfInfo());
        return svc;
    }

    /** consumer 自身节点信息。 */
    private Map<String, Object> selfInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("serviceName", "consumer");
        info.put("port", serverPort);
        try {
            InetAddress host = InetAddress.getLocalHost();
            info.put("hostName", host.getHostName());
            info.put("ipAddress", host.getHostAddress());
        } catch (Exception e) {
            info.put("hostName", "unknown");
            info.put("ipAddress", "unknown");
        }
        return info;
    }

    /** 安全获取本机主机名（Kafka 事件用） */
    private String safeLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 聚合单个 provider 的全部实例（pod）信息。
     * 通过 DiscoveryClient 从注册中心拿到所有实例，再逐个调用 /info，
     * 从而展示多 pod，而不是负载均衡只显示一个。
     */
    private Map<String, Object> buildServiceOverview(String serviceName, String displayName) {
        Map<String, Object> svc = new LinkedHashMap<>();
        svc.put("serviceName", serviceName);
        svc.put("displayName", displayName);

        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        int total = instances == null ? 0 : instances.size();
        svc.put("total", total);

        List<Map<String, Object>> instanceList = new ArrayList<>();
        int online = 0;

        if (instances != null) {
            // 用普通 RestTemplate 按 ip:port 直连每个实例
            for (ServiceInstance instance : instances) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("instanceId", instance.getInstanceId());
                row.put("scheme", instance.getScheme());
                row.put("port", instance.getPort());
                row.put("uri", String.valueOf(instance.getUri()));

                Map<String, Object> info = new LinkedHashMap<>();
                try {
                    String url = instance.getUri() + "/info";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = restTemplate.getForObject(url, Map.class);
                    if (body != null) {
                        info.putAll(body);
                    }
                    row.put("status", "UP");
                    online++;
                } catch (Exception e) {
                    info.put("error", "实例调用失败: " + e.getMessage());
                    row.put("status", "DOWN");
                }
                row.put("info", info);
                instanceList.add(row);
            }
        }
        svc.put("online", online);
        svc.put("offline", total - online);
        svc.put("instances", instanceList);
        return svc;
    }
}
