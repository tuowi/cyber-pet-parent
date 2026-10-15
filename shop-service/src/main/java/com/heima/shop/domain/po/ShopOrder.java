package com.heima.shop.domain.po;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.heima.api.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 订单表
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("shop_order")
public class ShopOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 购买用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 商品名称快照
     */
    private String productName;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 单价快照
     */
    private BigDecimal unitPrice;

    /**
     * 总价
     */
    private BigDecimal totalPrice;

    /**
     * PAID=已支付 / CANCELLED=已取消
     */
    private OrderStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
