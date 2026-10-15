package com.heima.pet.domain.redis;

import lombok.Data;

import java.util.Map;

@Data
public class InteractEventRedis {
    private String eventId;
    private String eventType;
    private Long userId;
    private Long petId;
    private Map<String, Object> data; // 或单独定义子类
    private Long timestamp;
}
