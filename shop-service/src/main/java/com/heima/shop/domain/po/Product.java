package com.heima.shop.domain.po;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.heima.api.enums.InteractionType;
import com.heima.api.enums.ProductStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 道具商品表
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 商品名称，如"小鱼干"
     */
    private String name;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 图标URL
     */
    private String icon;

    /**
     * 单价（虚拟币）
     */
    private BigDecimal price;

    /**
     * 当前库存数量
     */
    private Integer stock;

    /**
     * ON_SHELF=上架 / OFF_SHELF=下架
     */
    private ProductStatus status;

    /**
     * 每人每天限购数量，NULL=不限
     */
    private Integer dailyLimit;

    /**
     * 道具分类: FEED（食物）/ PET（玩具）/ BATHE（洗浴），NULL=纯装饰品，不可用于互动
     */
    private InteractionType effectType;

    /**
     * 使用后增加的饥饿值
     */
    private Integer effectHunger;

    /**
     * 使用后增加的心情值
     */
    private Integer effectMood;

    /**
     * 使用后增加的清洁值
     */
    private Integer effectCleanliness;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;


}
