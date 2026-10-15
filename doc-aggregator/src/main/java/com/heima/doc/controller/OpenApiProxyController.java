package com.heima.doc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAPI 文档代理控制器
 * <p>
 * 从各个微服务拉取 /v3/api-docs，将 server 地址统一替换为网关地址，
 * 然后通过 /openapi/{service} 端点暴露给 Knife4j 前端。
 */
@Slf4j
@RestController
public class OpenApiProxyController {

    /**
     * 文档页面的 server 地址 —— 指向 doc-aggregator 自身，
     * 再由 GatewayProxyFilter 透明代理到网关（避免跨域问题）
     */
    private static final String SERVER_URL = "http://localhost:8088";

    /**
     * 服务配置：key = 端点路径, value = 源 OpenAPI JSON 地址
     */
    private final Map<String, String> serviceMap = new LinkedHashMap<>() {{
        put("auth-service", "http://localhost:8086/v3/api-docs?group=default");
        put("pet-service", "http://localhost:8081/v3/api-docs?group=default");
        put("shop-service", "http://localhost:8082/v3/api-docs?group=default");
        put("social-service","http://localhost:8083/v3/api-docs?group=default");
        put("agent-service","http://localhost:8084/v3/api-docs?group=default");
        put("analytics-service","http://localhost:8085/v3/api-docs?group=default");
    }};

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动时验证所有服务可达
     */
    @PostConstruct
    public void checkServices() {
        serviceMap.forEach((name, url) -> {
            try {
                restTemplate.getForObject(url, String.class);
                log.info("✓ 服务 [{}] 文档可达: {}", name, url);
            } catch (Exception e) {
                log.warn("✗ 服务 [{}] 文档不可达: {} —— {}", name, url, e.getMessage());
            }
        });
    }

    /**
     * 代理所有 /openapi/* 请求，拉取原始文档并改写 server 地址
     */
    @GetMapping("/openapi/{service}")
    public ResponseEntity<String> getOpenApiDoc(@org.springframework.web.bind.annotation.PathVariable String service) {
        String sourceUrl = serviceMap.get(service);
        if (sourceUrl == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // 1. 从目标服务拉取原始 OpenAPI JSON
            String rawJson = restTemplate.getForObject(sourceUrl, String.class);
            if (rawJson == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("{\"error\":\"服务 " + service + " 返回空响应\"}");
            }

            // 2. 将 server 地址替换为网关地址
            String rewritten = rewriteServers(rawJson, service);

            return ResponseEntity.ok(rewritten);

        } catch (Exception e) {
            log.error("拉取服务 [{}] 文档失败: {}", service, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"无法连接服务 " + service + ": " + e.getMessage() + "\"}");
        }
    }

    /**
     * 将 OpenAPI JSON 中的 servers 字段替换为网关地址
     */
    private String rewriteServers(String rawJson, String serviceName) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            // 替换 servers
            ArrayNode servers = objectMapper.createArrayNode();
            ObjectNode server = objectMapper.createObjectNode();
            server.put("url", SERVER_URL);
            server.put("description", "网关入口");
            servers.add(server);
            ((ObjectNode) root).set("servers", servers);

            // 更新 info.description 加上来源标记
            if (root.has("info")) {
                ObjectNode info = (ObjectNode) root.get("info");
                String desc = info.has("description") ? info.get("description").asText() : "";
                info.put("description", desc + "（来源: " + serviceName + "，请求经 doc-aggregator → 网关）");
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.warn("改写 servers 失败，返回原始文档: {}", e.getMessage());
            return rawJson; // 降级：改写失败也返回原始文档
        }
    }
}
