package com.heima.pet.publisher;

import com.heima.api.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 宠物事件发布器 — 将互动/状态变化事件发布到 Redis Stream。
 *
 * <h3>消息流向</h3>
 * <pre>
 * pet-service (本类)  →  Redis Stream "stream:pet:events"
 *                    →  analytics-service EventStreamConsumer 消费
 *                    →  写入 intimacy_event + 更新排行榜 ZSet
 * </pre>
 *
 * <h3>消息格式</h3>
 * <pre>
 * {
 *   "eventId":      "雪花ID"        -- 幂等去重
 *   "eventType":    "FEED"          -- FEED/PET/BATHE/STATE_CHANGE
 *   "userId":       "1001"
 *   "petId":        "2001"
 *   "productId":    "3001"          -- 道具ID（免费互动时为 null）
 *   "scoreChange":  "5"             -- 亲密度变化量
 *   "hungerChange": "30"
 *   "moodChange":   "5"
 *   "cleanlinessChange": "0"
 *   "beforeState":  "NORMAL"
 *   "afterState":   "NORMAL"
 *   "timestamp":    "1720000000000"
 * }
 * </pre>
 *
 * @author lucy
 * @since 2026-07-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发布互动事件（喂食/抚摸/洗澡）。
     */
    public void publishInteractEvent(String eventId, String eventType,
                                     Long userId, Long petId,
                                     Long productId, int scoreChange,
                                     int hungerChange, int moodChange, int cleanlinessChange,
                                     String beforeState, String afterState) {
        Map<String, String> fields = new HashMap<>();
        fields.put("eventId", eventId);
        fields.put("eventType", eventType);
        fields.put("userId", String.valueOf(userId));
        fields.put("petId", String.valueOf(petId));
        fields.put("productId", productId != null ? String.valueOf(productId) : "");
        fields.put("scoreChange", String.valueOf(scoreChange));
        fields.put("hungerChange", String.valueOf(hungerChange));
        fields.put("moodChange", String.valueOf(moodChange));
        fields.put("cleanlinessChange", String.valueOf(cleanlinessChange));
        fields.put("beforeState", beforeState);
        fields.put("afterState", afterState);
        fields.put("timestamp", String.valueOf(System.currentTimeMillis()));

        try {
            StringRecord record = StreamRecords.string(fields)
                    .withStreamKey(RedisKeyPrefix.STREAM_PET_EVENTS_KEY);
            stringRedisTemplate.opsForStream().add(record);
            log.debug("Stream 事件发布成功: eventId={}, eventType={}, petId={}", eventId, eventType, petId);
        } catch (Exception e) {
            // 发布失败不阻塞主流程
            log.error("Stream 事件发布失败: eventId={}, eventType={}", eventId, eventType, e);
        }
    }

    /**
     * 发布状态变化事件（衰减离家出走/找回等）。
     */
    public void publishStateChangeEvent(String eventId, Long userId, Long petId,
                                        int scoreChange,
                                        String beforeState, String afterState,
                                        String triggerType) {
        Map<String, String> fields = new HashMap<>();
        fields.put("eventId", eventId);
        fields.put("eventType", "STATE_CHANGE");
        fields.put("userId", String.valueOf(userId));
        fields.put("petId", String.valueOf(petId));
        fields.put("productId", "");
        fields.put("scoreChange", String.valueOf(scoreChange));
        fields.put("hungerChange", "0");
        fields.put("moodChange", "0");
        fields.put("cleanlinessChange", "0");
        fields.put("beforeState", beforeState);
        fields.put("afterState", afterState);
        fields.put("triggerType", triggerType);
        fields.put("timestamp", String.valueOf(System.currentTimeMillis()));

        try {
            StringRecord record = StreamRecords.string(fields)
                    .withStreamKey(RedisKeyPrefix.STREAM_PET_EVENTS_KEY);
            stringRedisTemplate.opsForStream().add(record);
            log.info("状态变化事件发布: eventId={}, petId={}, {}→{}", eventId, petId, beforeState, afterState);
        } catch (Exception e) {
            log.error("状态变化事件发布失败: eventId={}", eventId, e);
        }
    }
}
