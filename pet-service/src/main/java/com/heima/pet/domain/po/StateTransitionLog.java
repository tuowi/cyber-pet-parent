package com.heima.pet.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.heima.api.enums.PetState;
import com.heima.api.enums.TriggerType;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * <p>
 * 状态流转日志表
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("state_transition_log")
public class StateTransitionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 宠物ID
     */
    @TableField("pet_id")
    private Long petId;

    /**
     * 原状态
     */
    @TableField("from_state")
    private PetState fromState;

    /**
     * 新状态
     */
    @TableField("to_state")
    private PetState toState;

    /**
     * 触发方式: DECAY/INTERACT/TIMEOUT/MANUAL
     */
    @TableField("trigger_type")
    private TriggerType triggerType;

    /**
     * 补充描述
     */
    @TableField("description")
    private String description;

    @TableField("create_time")
    private LocalDateTime createTime;


}
