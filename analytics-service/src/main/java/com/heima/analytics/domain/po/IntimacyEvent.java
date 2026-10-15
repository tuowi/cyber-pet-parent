package com.heima.analytics.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.heima.api.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 亲密度事件流水表
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("intimacy_event")
public class IntimacyEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 业务事件ID（来自 Stream 消息），用于幂等去重
     */
    private String eventId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 宠物ID
     */
    private Long petId;

    /**
     * 事件类型: FEED/PLAY/BATHE/STEALED
     */
    private EventType eventType;

    /**
     * 亲密度变化量（正或负）
     */
    private Integer scoreChange;

    /**
     * 变化后的亲密度值
     */
    private Integer currentScore;

    private LocalDateTime createTime;


}
