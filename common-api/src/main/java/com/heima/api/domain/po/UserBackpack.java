package com.heima.api.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户背包表
 * </p>
 *
 * @author lucy
 * @since 2026-07-22
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@TableName("user_backpack")
public class UserBackpack implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 当前持有数量
     */
    private Integer quantity;

    /**
     * 累计获得数量
     */
    private Integer totalAcquired;

    /**
     * 累计消耗数量
     */
    private Integer totalConsumed;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
