package com.heima.social.config;

import com.heima.api.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 生命周期事件处理：连接上线 / 断线下线
 *
 * <h3>和 OnlineHeartbeatScheduler 的关系</h3>
 * <pre>
 * 5.1 HTTP 心跳 (POST /api/social/heartbeat)  → 每25秒续期 TTL（30s）
 * SessionConnect（本类）                        → 连接建立时立即标记在线（TTL 60s）
 * SessionDisconnect（本类）                     → 连接断开时立即清除（不等 TTL 过期）
 * OnlineHeartbeatScheduler                      → 兜底：每30秒清理 Geo 中已离线的用户
 * </pre>
 *
 * @author lucy
 * @since 2026-07-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 用户建立 WebSocket 连接 — 标记在线
     * <p>
     * TTL 设为 60s（比 HTTP 心跳的 30s 长），原因：
     * 客户端每隔 25s 发一次 HTTP 心跳续期。
     * 如果只靠 WS 连接（STOMP heartbeat 10s），万一 STOMP heartbeat
     * 漏了一次，TTL 还有 60s 兜底，不会误判离线。
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user == null || user.getName() == null) {
            return;
        }

        String userId = user.getName();
        String onlineKey = RedisKeyPrefix.SOCIAL_ONLINE_KEY + userId;
        stringRedisTemplate.opsForValue().set(onlineKey, "1", 60, TimeUnit.SECONDS);
        log.info("用户上线(WS): userId={}", userId);
    }

    /**
     * 用户断开 WebSocket 连接 — 立即清离线标记 + Geo
     * <p>
     * 为什么不依赖 TTL 自动过期？
     * 依赖 TTL 最多延迟 60s，其他用户可能在"附近的人"里看到已离线用户。
     * 立即清理 Geo 确保"附近的人"数据实时准确。
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user == null || user.getName() == null) {
            return;
        }

        String userId = user.getName();

        // 1. 清除在线标记
        String onlineKey = RedisKeyPrefix.SOCIAL_ONLINE_KEY + userId;
        stringRedisTemplate.delete(onlineKey);

        // 2. 从附近的人中移除
        stringRedisTemplate.opsForGeo().remove(RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY, userId);

        log.info("用户离线(WS): userId={}", userId);
    }
}
