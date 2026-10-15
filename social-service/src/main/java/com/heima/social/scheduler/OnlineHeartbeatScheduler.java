package com.heima.social.scheduler;

import com.heima.api.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线用户清理定时任务——每 30 秒清理 Geo 中已离线的用户
 *
 * <h3>为什么需要这个任务？</h3>
 * <pre>
 * 在线标记 (social:online:{userId})      → Redis String, TTL 30s
 * 地理坐标 (social:geo:nearby)           → Redis Geo, 无 TTL
 * </pre>
 *
 * 问题：online key 过期后，Geo 里可能残留已离线用户的坐标。
 * 虽然 WebSocketEventListener 在断连时会 DELETE + ZREM，
 * 但以下场景可能漏掉：
 * <ul>
 *   <li>客户端直接关浏览器（没发 DISCONNECT 帧）</li>
 *   <li>网络中断（STOMP heartbeat 超时才触发 disconnect）</li>
 *   <li>服务重启（WS 连接全断，但 Geo 数据持久在 Redis）</li>
 * </ul>
 *
 * 所以每 30 秒做一次兜底扫描：遍历 Geo 中所有用户，把 online key 已过期的剔掉。
 *
 * <h3>和 WebSocketEventListener 的分工</h3>
 * WebSocketEventListener   → 立即响应（断连马上清理，最快）
 * 本定时任务              → 兜底扫描（30 秒一次，保证最终一致）
 *
 * @author lucy
 * @since 2026-07-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlineHeartbeatScheduler {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 每 30 秒执行一次——和 online key 的 TTL 保持一致
     * <p>
     * 为什么不是每 5 秒？本任务是兜底，不追求实时，
     * 30 秒间隔 = 最多延迟 30 秒清理（对用户体验影响很小）。
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void cleanOfflineUsers() {
        // 1. 获取 Geo 中所有成员（只取 member 名，不需要距离）
        // Redis GEORADIUS 有个取巧方式：用极大半径获取所有成员
        // 但更直接的方式是用 ZRANGE（Geo 底层是 ZSet）
        // Spring Data Redis 没有直接获取全部 Geo 成员的 API，
        // 我们用 ZSet 操作替代（Geo 的 key 也是一个 ZSet）
        try {
            log.info("定时清理过期用户任务触发");
            // social:geo:nearby 同时是一个 ZSet，可以用 ZSet 操作
            Long size = stringRedisTemplate.opsForZSet()
                    .size(RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY);
            if (size == null || size == 0) {
                log.info("Geo 集合为空，跳过清理");
                return;
            }

            // 获取全部成员
            var members = stringRedisTemplate.opsForZSet()
                    .range(RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY, 0, size - 1);
            if (members == null || members.isEmpty()) {
                log.info("Geo 集合无成员，跳过清理");
                return;
            }

            // 2. 检查每个用户是否在线，不在线则移除
            List<String> toRemove = new ArrayList<>();
            for (String userId : members) {
                String onlineKey = RedisKeyPrefix.SOCIAL_ONLINE_KEY + userId;
                Boolean exists = stringRedisTemplate.hasKey(onlineKey);
                if (Boolean.FALSE.equals(exists)) {
                    toRemove.add(userId);
                }
            }

            // 3. 批量移除离线用户
            if (!toRemove.isEmpty()) {
                stringRedisTemplate.opsForGeo()
                        .remove(RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY, toRemove.toArray(new String[0]));
                log.info("清理离线用户(Geo): 移除 {} 个, 当前在线 {}", toRemove.size(), members.size() - toRemove.size());
            } else {
                log.info("无离线用户需清理, 当前在线 {}", members.size());
            }
        } catch (Exception e) {
            log.error("清理离线用户失败", e);
        }
    }
}
