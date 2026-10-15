package com.heima.analytics.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.analytics.domain.po.IntimacyEvent;
import com.heima.analytics.mapper.IntimacyEventMapper;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.enums.EventType;
import com.heima.common.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <h3>Redis Stream 消费者 — 双 Stream 消费，写入亲密度流水并更新排行榜</h3>
 *
 * <h3>消费的 Stream</h3>
 * <table border="1">
 *   <tr><th>Stream</th><th>发布方</th><th>事件类型</th><th>映射到 EventType</th></tr>
 *   <tr><td>stream:pet:events</td><td>pet-service</td>
 *       <td>FEED / PET / BATHE / STATE_CHANGE</td>
 *       <td>FEED / PLAY / BATHE / （仅 ZSet，不写表）</td></tr>
 *   <tr><td>stream:social:events</td><td>social-service</td>
 *       <td>STEAL_SNACK</td><td>STEALED（被害宠物 -5 亲密度）</td></tr>
 * </table>
 *
 * <h3>社交事件字段映射（偷零食）</h3>
 * <pre>
 * Social 消息: fromUserId=小偷, toUserId=被害者, petId=小偷宠物, targetPetId=被害宠物
 * 亲密度记录:  userId=被害者(toUserId), petId=被害宠物(targetPetId), scoreChange=-5
 * </pre>
 *
 * <h3>STATE_CHANGE 特殊处理</h3>
 * 衰减/离家出走等状态变化事件不在 EventType 枚举中（已精简为 4 类），
 * 但其 scoreChange（-10 或 -50）仍影响亲密度。处理方式：
 * 仅 ZINCRBY 更新排行榜，不写入 intimacy_event 表。
 *
 * <h3>幂等保证</h3>
 * intimacy_event 表 event_id 字段有 UNIQUE 约束，
 * 重复消费同一条消息时 DB 抛 DuplicateKeyException，捕获后回滚 ZSet 并 ACK。
 *
 * @author lucy
 * @since 2026-07-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventStreamConsumer {

    private final StringRedisTemplate stringRedisTemplate;
    private final IntimacyEventMapper intimacyEventMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    // ========== Stream 配置 ==========

    private static final String PET_STREAM = RedisKeyPrefix.STREAM_PET_EVENTS_KEY;
    private static final String SOCIAL_STREAM = RedisKeyPrefix.STREAM_SOCIAL_EVENTS_KEY;
    private static final String CONSUMER_GROUP = "analytics-consumer-group";
    private static final String CONSUMER_NAME = "analytics-consumer";

    // ========== 宠物事件消费 (stream:pet:events) ==========

    /**
     * 每 2 秒 poll 一次 {@code stream:pet:events}（喂食/抚摸/洗澡/状态变化）。
     */
    @Scheduled(fixedDelay = 2000)
    public void consumePetEvents() {
        try {
            ensureConsumerGroup(PET_STREAM);

            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> messages = stringRedisTemplate.opsForStream()
                    .read(
                            Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                            StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                            StreamOffset.create(PET_STREAM, ReadOffset.from(">"))
                    );

            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> record : messages) {
                try {
                    processPetRecord(record);
                } catch (Exception e) {
                    log.error("处理宠物事件失败: recordId={}", record.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("宠物事件消费循环异常", e);
        }
    }

    // ========== 社交事件消费 (stream:social:events) ==========

    /**
     * 每 2 秒 poll 一次 {@code stream:social:events}（偷零食）。
     *
     * <p>注意：社交消息的字段命名不同于宠物消息——
     * 被害者是 {@code toUserId}，被害宠物是 {@code targetPetId}。亲密度固定 -5。</p>
     */
    @Scheduled(fixedDelay = 2000)
    public void consumeSocialEvents() {
        try {
            ensureConsumerGroup(SOCIAL_STREAM);

            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> messages = stringRedisTemplate.opsForStream()
                    .read(
                            Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                            StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                            StreamOffset.create(SOCIAL_STREAM, ReadOffset.from(">"))
                    );

            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> record : messages) {
                try {
                    processSocialRecord(record);
                } catch (Exception e) {
                    log.error("处理社交事件失败: recordId={}", record.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("社交事件消费循环异常", e);
        }
    }

    // ========== Pending 消息重试 ==========

    /**
     * 每 5 分钟重试两个 Stream 的 pending 消息（投递了但未 ACK）。
     */
    @Scheduled(fixedDelay = 300_000)
    public void retryPending() {
        retryPendingStream(PET_STREAM);
        retryPendingStream(SOCIAL_STREAM);
    }

    private void retryPendingStream(String streamKey) {
        try {
            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> pending = stringRedisTemplate.opsForStream()
                    .read(
                            Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                            StreamReadOptions.empty().count(50),
                            StreamOffset.create(streamKey, ReadOffset.from("0"))
                    );

            if (pending == null || pending.isEmpty()) {
                return;
            }

            log.info("发现 {} 条 pending 消息 (stream={}), 开始重试", pending.size(), streamKey);
            for (MapRecord<String, Object, Object> record : pending) {
                try {
                    if (PET_STREAM.equals(streamKey)) {
                        processPetRecord(record);
                    } else {
                        processSocialRecord(record);
                    }
                } catch (Exception e) {
                    log.error("重试 pending 消息失败: stream={}, recordId={}", streamKey, record.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Pending 重试异常: stream={}", streamKey, e);
        }
    }

    // ========== 消息处理 ==========

    /**
     * 处理宠物 Stream 消息（FEED / PET / BATHE / STATE_CHANGE）。
     */
    private void processPetRecord(MapRecord<String, Object, Object> record) {
        Map<String, String> fields = toStringMap(record.getValue());
        String eventId = fields.get("eventId");
        if (isBlank(eventId)) {
            ack(PET_STREAM, record);
            return;
        }

        // 幂等检查
        if (isDuplicate(eventId)) {
            ack(PET_STREAM, record);
            return;
        }

        String rawType = fields.get("eventType");
        Long userId = parseLong(fields, "userId");
        Long petId = parseLong(fields, "petId");
        int scoreChange = parseInt(fields, "scoreChange");

        if (userId == null || petId == null) {
            log.warn("宠物事件字段缺失: eventId={}", eventId);
            ack(PET_STREAM, record);
            return;
        }

        // 事件类型映射（STATE_CHANGE → null，只调 ZSet）
        EventType eventType = mapEventType(rawType);
        saveEvent(PET_STREAM, record, eventId, userId, petId, eventType, scoreChange);
    }

    /**
     * 处理社交 Stream 消息（STEAL_SNACK）。
     *
     * <p>字段映射：
     * <ul>
     *   <li>被害用户 = {@code toUserId}</li>
     *   <li>被害宠物 = {@code targetPetId}</li>
     *   <li>亲密度变化 = -5</li>
     * </ul>
     */
    private void processSocialRecord(MapRecord<String, Object, Object> record) {
        Map<String, String> fields = toStringMap(record.getValue());
        String eventId = fields.get("eventId");
        if (isBlank(eventId)) {
            ack(SOCIAL_STREAM, record);
            return;
        }

        // 幂等检查
        if (isDuplicate(eventId)) {
            ack(SOCIAL_STREAM, record);
            return;
        }

        // 被害者是 toUserId，被害宠物是 targetPetId
        Long userId = parseLong(fields, "toUserId");
        Long petId = parseLong(fields, "targetPetId");

        if (userId == null || petId == null) {
            log.warn("社交事件字段缺失: eventId={}, toUserId={}, targetPetId={}",
                    eventId, fields.get("toUserId"), fields.get("targetPetId"));
            ack(SOCIAL_STREAM, record);
            return;
        }

        // STEAL_SNACK → EventType.STEALED，亲密度固定 -5
        saveEvent(SOCIAL_STREAM, record, eventId, userId, petId, EventType.STEALED, -5);
    }

    /**
     * 保存亲密度事件：更新 ZSet 排行榜 → 写入 intimacy_event 表（可选） → ACK。
     *
     * @param eventType  事件类型；为 null 时仅更新 ZSet（STATE_CHANGE 场景），不写 DB
     * @param scoreChange 亲密度变化量
     */
    private void saveEvent(String streamKey, MapRecord<String, Object, Object> record,
                           String eventId, Long userId, Long petId,
                           EventType eventType, int scoreChange) {
        String member = userId + ":" + petId;

        // 1. 更新排行榜 ZSet
        Double newScoreObj = stringRedisTemplate.opsForZSet()
                .incrementScore(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, member, scoreChange);
        int currentScore = newScoreObj != null ? newScoreObj.intValue() : scoreChange;

        // 2. 若有映射的 EventType → 写入 DB
        if (eventType != null) {
            IntimacyEvent event = new IntimacyEvent()
                    .setId(snowflakeIdGenerator.nextId())
                    .setEventId(eventId)
                    .setUserId(userId)
                    .setPetId(petId)
                    .setEventType(eventType)
                    .setScoreChange(scoreChange)
                    .setCurrentScore(currentScore)
                    .setCreateTime(LocalDateTime.now());

            try {
                intimacyEventMapper.insert(event);
                log.debug("亲密度事件写入: eventId={}, type={}, petId={}, score={}+{}→{}",
                        eventId, eventType, petId, currentScore - scoreChange, scoreChange, currentScore);
            } catch (DuplicateKeyException e) {
                // 并发重复 → 回滚 ZSet
                log.info("并发重复 eventId={}，回滚 ZSet", eventId);
                stringRedisTemplate.opsForZSet()
                        .incrementScore(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, member, -scoreChange);
            }
        } else {
            log.debug("STATE_CHANGE 事件仅更新 ZSet: eventId={}, petId={}, scoreChange={}",
                    eventId, petId, scoreChange);
        }

        // 3. ACK
        ack(streamKey, record);
    }

    /**
     * 将 Stream 消息中的原始 eventType 映射为 EventType 枚举。
     *
     * <p>映射规则：
     * <ul>
     *   <li>FEED → {@link EventType#FEED}</li>
     *   <li>PET（抚摸） → {@link EventType#PLAY}（玩耍）</li>
     *   <li>BATHE → {@link EventType#BATHE}</li>
     *   <li>STEAL_SNACK → {@link EventType#STEALED}</li>
     *   <li>STATE_CHANGE → null（不在 EventType 枚举中，仅更新 ZSet）</li>
     * </ul>
     *
     * @return 对应的 EventType，无匹配则返回 null
     */
    private EventType mapEventType(String rawType) {
        if (rawType == null) return null;
        return switch (rawType) {
            case "FEED"         -> EventType.FEED;
            case "PET"          -> EventType.PLAY;     // 抚摸 → 玩耍
            case "BATHE"        -> EventType.BATHE;
            case "STEAL_SNACK"  -> EventType.STEALED;  // 被偷零食
            case "STATE_CHANGE" -> EventType.STATE_CHANGE;                // 仅 ZSet，不写 DB
            default -> {
                log.warn("未知事件类型: {}", rawType);
                yield null;
            }
        };
    }

    // ========== 工具方法 ==========

    private void ensureConsumerGroup(String streamKey) {
        // 先检查 consumer group 是否已存在，避免 XGROUP CREATE 触发 BUSYGROUP 错误
        try {
            StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(streamKey);
            boolean groupExists = groups.stream()
                    .anyMatch(g -> CONSUMER_GROUP.equals(g.groupName()));
            if (groupExists) {
                return;
            }
        } catch (Exception e) {
            // Stream 可能尚未创建，忽略异常，继续尝试创建
        }

        try {
            // 使用 ReadOffset.from("0") 从头开始读取，确保 consumer group 创建前的
            // 历史消息也不会丢失（默认的 $ 只接收创建之后的新消息）
            stringRedisTemplate.opsForStream().createGroup(
                    streamKey, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("Consumer group 创建: {} / {} (offset=0)", streamKey, CONSUMER_GROUP);
        } catch (Exception ignored) {
            // 并发场景下 group 可能已被其他线程创建，
            // 或 stream 不存在（social-service 尚未发布第一条消息），等下次轮询重试
        }
    }

    private boolean isDuplicate(String eventId) {
        long count = intimacyEventMapper.selectCount(
                new LambdaQueryWrapper<IntimacyEvent>()
                        .eq(IntimacyEvent::getEventId, eventId));
        if (count > 0) {
            log.debug("重复消息 eventId={}，跳过", eventId);
            return true;
        }
        return false;
    }

    private boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Map<Object, Object> rawMap) {
        return (Map<String, String>) (Map<?, ?>) rawMap;
    }

    private Long parseLong(Map<String, String> fields, String key) {
        try {
            String val = fields.get(key);
            return val != null && !val.isEmpty() ? Long.parseLong(val) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseInt(Map<String, String> fields, String key) {
        try {
            String val = fields.get(key);
            return val != null && !val.isEmpty() ? Integer.parseInt(val) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void ack(String streamKey, MapRecord<String, Object, Object> record) {
        try {
            stringRedisTemplate.opsForStream()
                    .acknowledge(streamKey, CONSUMER_GROUP, record.getId().getValue());
        } catch (Exception e) {
            log.error("ACK 失败: stream={}, recordId={}", streamKey, record.getId(), e);
        }
    }
}
