package com.heima.pet.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 宠物属性表（MySQL 持久化副本）
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("pet_attribute")
public class PetAttribute implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 pet_info.id
     */
    @TableField("pet_id")
    private Long petId;

    /**
     * 饥饿值 0-100，越低越饿
     */
    @TableField("hunger")
    private Integer hunger;

    /**
     * 心情值 0-100，越低越差
     */
    @TableField("mood")
    private Integer mood;

    /**
     * 清洁值 0-100，越低越脏
     */
    @TableField("cleanliness")
    private Integer cleanliness;

    /**
     * 上次衰减时间
     */
    @TableField("last_decay_time")
    private LocalDateTime lastDecayTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;


}
