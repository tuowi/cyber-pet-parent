package com.heima.pet.domain.po;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.io.Serializable;

import com.heima.api.enums.Personality;
import com.heima.api.enums.PetState;
import com.heima.api.enums.Species;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 宠物基础信息表
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("pet_info")
public class PetInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 主人用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 宠物昵称
     */
    @TableField("pet_name")
    private String petName;

    /**
     * 物种: CAT/DOG
     */
    @TableField("species")
    private Species species;

    /**
     * 性格: 傲娇/粘人/贪吃/佛系
     */
    @TableField("personality")
    private String personality;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 当前状态: NORMAL/HUNGRY/ANGRY/SICK/RUNAWAY
     */
    @TableField("state")
    private PetState state;

    /**
     * 离家出走时间，NULL=未出走（兜底查询用）
     */
    @TableField("runaway_time")
    private LocalDateTime runawayTime;

    /**
     * 领养时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 0=正常 1=已删除
     */
    @TableField("deleted")
    private Integer deleted;


}
