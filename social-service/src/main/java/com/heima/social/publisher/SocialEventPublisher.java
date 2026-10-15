package com.heima.social.publisher;

// 文件: social-service/.../publisher/SocialEventPublisher.java

import cn.hutool.json.JSONUtil;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.social.event.SocialEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class SocialEventPublisher {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将社交事件发布到 Redis Stream。
     * analytics-service 的 EventStreamConsumer 会异步消费。
     */
    public void publish(SocialEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", event.getEventId());
        fields.put("eventType", event.getEventType());
        fields.put("fromUserId", String.valueOf(event.getFromUserId()));
        fields.put("toUserId", String.valueOf(event.getToUserId()));
        fields.put("petId", event.getPetId() != null ? String.valueOf(event.getPetId()) : "");
        fields.put("targetPetId", event.getTargetPetId() != null ? String.valueOf(event.getTargetPetId()) : "");
        fields.put("timestamp", String.valueOf(event.getTimestamp()));

        // data 扩展字段序列化为 JSON 子对象
        if (event.getData() != null && !event.getData().isEmpty()) {
            fields.put("data", JSONUtil.toJsonStr(event.getData()));
        }

        try {
            RecordId recordId = stringRedisTemplate.opsForStream()
                    .add(RedisKeyPrefix.STREAM_SOCIAL_EVENTS_KEY, fields);
            log.info("SocialEvent published: eventType={}, streamId={}", event.getEventType(), recordId);
        } catch (Exception e) {
            log.error("Failed to publish SocialEvent: eventType={}, toUserId={}",
                    event.getEventType(), event.getToUserId(), e);
            // Stream 发布失败不阻塞主流程（analytics 消费是异步增强）
        }
    }
}

