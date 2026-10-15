package com.heima.social.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialEvent {
    private String eventId;        // Snowflake ID 字符串，用于 analytics-service 幂等去重
    private String eventType;      // "STEAL_SNACK" / "PLAY_MATCH" / "VISIT"
    private Long fromUserId;       // 发起方
    private Long toUserId;         // 目标方
    private Long petId;            // 发起方宠物
    private Long targetPetId;      // 目标方宠物
    private Map<String, Object> data;  // 扩展字段: {reward, moodChange, ...}
    private Long timestamp;        // System.currentTimeMillis()
}
