package com.heima.doc.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * API 请求反向代理过滤器
 * <p>
 * 将 Knife4j 文档页面的 "Try it out" 请求从 doc-aggregator（8088）
 * 转发到网关（8080），由网关完成 JWT 鉴权和路由。
 * <p>
 * 直接使用 {@link java.net.http.HttpClient} 原生 API，不经过
 * {@link org.springframework.web.client.RestTemplate}，避免消息转换器
 * 对请求头（尤其是 Authorization）的干扰。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GatewayProxyFilter implements Filter {

    private static final String GATEWAY = "http://localhost:8080";

    /**
     * 不需要代理的路径 —— 文档页面自身的资源
     */
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/doc.html",
            "/webjars",
            "/openapi",
            "/v3/api-docs",
            "/swagger-resources",
            "/swagger-ui",
            "/favicon.ico",
            "/error"
    );

    /**
     * 转发时需要跳过的请求头 —— 由 HttpClient 根据实际请求自动设置
     */
    private static final List<String> SKIP_REQUEST_HEADERS = List.of(
            "host",
            "content-length",
            "transfer-encoding",
            "connection",
            "expect",
            "upgrade"
    );

    /**
     * Java 11 原生 HttpClient：不做 401 自动重试，不对请求头做任何篡改
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        String path = request.getRequestURI();

        // 文档相关的请求直接放行
        if (SKIP_PREFIXES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        // === 其他所有请求（API 测试请求）→ 转发到网关 ===
        String query = request.getQueryString();
        String gatewayUrl = GATEWAY + path + (query != null ? "?" + query : "");

        log.debug("代理请求: {} {} → {}", request.getMethod(), path, gatewayUrl);

        try {
            // 1. 读取请求体
            byte[] body = request.getInputStream().readAllBytes();

            // 2. 构建目标请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl))
                    .method(request.getMethod(),
                            body.length > 0
                                    ? HttpRequest.BodyPublishers.ofByteArray(body)
                                    : HttpRequest.BodyPublishers.noBody());

            // 3. 复制请求头 —— 跳过 HttpClient 自动管理的头
            Collections.list(request.getHeaderNames()).forEach(name -> {
                if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                    Collections.list(request.getHeaders(name))
                            .forEach(value -> requestBuilder.header(name, value));
                }
            });

            // 4. 发送请求，拿到原始响应
            HttpResponse<byte[]> gatewayResp = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            // 5. 透传状态码
            response.setStatus(gatewayResp.statusCode());

            // 6. 透传 Content-Type
            gatewayResp.headers().firstValue("Content-Type")
                    .ifPresent(response::setContentType);

            // 7. 透传响应体
            byte[] respBody = gatewayResp.body();
            if (respBody != null && respBody.length > 0) {
                response.getOutputStream().write(respBody);
            }

        } catch (IOException e) {
            log.error("代理请求失败 (网络层): {} {} —— {}", request.getMethod(), path, e.getMessage());
            response.setStatus(HttpStatus.BAD_GATEWAY.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":502,\"message\":\"无法连接到网关: " + e.getMessage() + "\",\"data\":null}"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("代理请求被中断: {} {}", request.getMethod(), path);
            response.setStatus(HttpStatus.BAD_GATEWAY.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":502,\"message\":\"代理请求被中断\",\"data\":null}"
            );
        }
    }
}
