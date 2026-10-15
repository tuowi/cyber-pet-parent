package com.heima.analytics.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.EventType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecentEvents {
    private String eventId;
    private EventType eventType;
    private String eventName;
    private Integer scoreChange;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
