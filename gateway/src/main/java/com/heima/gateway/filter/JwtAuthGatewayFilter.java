package com.heima.gateway.filter;

import cn.hutool.core.util.StrUtil;
import com.heima.common.constant.RedisKeyPrefix;
import com.heima.common.domain.Result;
import com.heima.gateway.config.AuthProperties;
import com.heima.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 全局鉴权过滤器
 * <p>
 * 职责：
 * 1. 校验请求头中的 Bearer Token
 * 2. 白名单路径放行（登录、注册等）
 * 3. 解析 userId 注入 X-User-Id 请求头，下游服务通过 UserContextFilter 读取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "User-Id";
    private static final String USER_ROLE_HEADER = "User-Role";
    private static final String USER_JTI_HEADER = "User-Jti";

    /**
     * 白名单路径 —— 无需鉴权即可访问
     */
    private final AuthProperties authProperties;

    private final JwtProperties jwtProperties;

    private final StringRedisTemplate stringRedisTemplate;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        String path = exchange.getRequest().getURI().getPath();

        // 1. 白名单路径直接放行
        if (isExclude(exchange.getRequest().getPath().toString())) {
            //放行
            return chain.filter(exchange);
        }

        // 2. 提取 Token
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StrUtil.isBlank(token) || !token.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少Token或格式错误");
        }

        // 3. 解析 JWT，提取 userId 和 role
        try {
            Claims claims = parseClaims(token.substring(7));
            Long userId = Long.valueOf(claims.getSubject());
            String role = (String) claims.get("role");
            String jti = (String) claims.get("jti");

            // 检验是否已经退出登录（jti 为 null 说明是老 Token，跳过检查）
            if (StrUtil.isNotBlank(jti)) {
                String logoutKey = RedisKeyPrefix.LOGOUT_TOKENS_KEY + userId;
                if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(logoutKey, jti))) {
                    return unauthorized(exchange, "已经退出登录");
                }
            }

            // 4. 将 userId、role、jti 注入请求头，下游服务从 UserContextHolder 读取
            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .header(USER_ID_HEADER, String.valueOf(userId))
                    .header(USER_ROLE_HEADER, role != null ? role : "USER")
                    .header(USER_JTI_HEADER, jti != null ? jti : "")
                    .build();

            return chain.filter(exchange.mutate().request(newRequest).build());

        } catch (JwtException e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return unauthorized(exchange, "Token无效或已过期");
        } catch (Exception e) {
            log.error("网关鉴权异常", e);
            return unauthorized(exchange, "鉴权服务异常");
        }
    }

    @Override
    public int getOrder() {
        // 数值越小优先级越高，鉴权过滤器应优先执行
        return -100;
    }

    // ==================== 内部方法 ====================

    /**
     * 从 JWT Token 中解析 Claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 返回 401 未授权响应（JSON 格式）
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    private boolean isExclude(String path) {
        for (String pathPattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pathPattern, path)) {
                return true;
            }
        }
        return false;
    }
}
