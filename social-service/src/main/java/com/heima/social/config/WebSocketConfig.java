package com.heima.social.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP over WebSocket 总配置
 *
 * <h3>客户端连接地址</h3>
 * <pre>ws://localhost:8080/ws/social</pre>
 * （经 Gateway 转发到 social-service:8083，握手阶段 Gateway 校验 JWT）
 *
 * <h3>为什么不用你已有的 UserInfoInterceptor？</h3>
 * UserInfoInterceptor 是 Spring MVC 的 HandlerInterceptor，
 * 只能拦截 DispatcherServlet 处理的 HTTP 请求。
 * WebSocket 的握手虽然走 HTTP，但由 WebSocket 引擎处理，
 * 不走 DispatcherServlet → UserInfoInterceptor 拦不到。
 * 所以需要 WebSocketAuthInterceptor（HandshakeInterceptor）。
 *
 * <h3>消息路由速查</h3>
 * <pre>
 * /topic/*      公共广播 — 所有订阅者都收到（如热门帖子更新）
 * /queue/*      个人队列 — 只有目标用户收到（如被偷通知、匹配请求）
 * /app/*        客户端 → 服务端（@MessageMapping 处理，目前暂不需要）
 * /user/*       Spring 自动拼接的用户私有前缀
 * </pre>
 *
 * convertAndSendToUser("李四ID", "/queue/steal", msg)
 * → 实际推送到 /user/李四ID/queue/steal
 * → 只有 Principal.getName() == "李四ID" 的连接能收到
 *
 * @author lucy
 * @since 2026-07-24
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 注册 STOMP 端点
     *
     * WebSocketAuthInterceptor 是 HandshakeInterceptor，
     * 在 HTTP→WebSocket 握手阶段读取 Gateway 注入的 User-Id 头，
     * 并设成 WebSocket 会话的 Principal。
     * 之后 convertAndSendToUser 就靠这个 Principal 来路由。
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/social")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    /**
                     * 从 WebSocketAuthInterceptor 存入的 attributes 中取出 userId，
                     * 包装成 Principal。这样 convertAndSendToUser("userId", ...)
                     * 才能正确匹配到这个连接。
                     */
                    @Override
                    protected Principal determineUser(
                            org.springframework.http.server.ServerHttpRequest request,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes) {
                        String userId = (String) attributes.get("userId");
                        if (userId != null) {
                            return () -> userId;  // Principal.getName() 返回 userId
                        }
                        return null;  // 无身份用户（后续 convertAndSendToUser 推不到他）
                    }
                })
                .addInterceptors(new WebSocketAuthInterceptor())
                .setAllowedOriginPatterns("*");
    }

    /**
     * 配置消息代理
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 内置消息代理：客户端可以订阅 /topic/* 和 /queue/*
        registry.enableSimpleBroker("/topic", "/queue");

        // 客户端主动发消息给服务端时，destination 必须带 /app 前缀
        // 例如：SEND /app/match-request → @MessageMapping("/match-request")
        registry.setApplicationDestinationPrefixes("/app");

        // 用户私有队列前缀，convertAndSendToUser 依赖它
        registry.setUserDestinationPrefix("/user");
    }
}
