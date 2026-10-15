package com.heima.social.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;

import com.heima.api.enums.SocialInteractionStatus;
import com.heima.api.enums.SocialInteractionType;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 社交互动记录表
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interaction_record")
public class InteractionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 发起方用户ID
     */
    private Long fromUserId;

    @TableField(exist = false)
    private String fromNickname;

    /**
     * 目标方用户ID
     */
    private Long toUserId;

    @TableField(exist = false)
    private String toNickname;

    /**
     * 发起方宠物ID
     */
    private Long petId;

    @TableField(exist = false)
    private String petName;

    /**
     * 目标方宠物ID
     */
    private Long targetPetId;

    @TableField(exist = false)
    private String targetPetName;

    /**
     * 互动类型: STEAL_SNACK / PLAY_MATCH / VISIT
     */
    private SocialInteractionType type;

    @TableField(exist = false)
    private String typeName;

    /**
     * SUCCESS / FAILED
     */
    private SocialInteractionStatus status;

    private LocalDateTime createTime;

    /**
     * 状态变更时间
     */
    private LocalDateTime updateTime;


}
