package com.heima.shop.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "下单返回体")
public class OrderVO {
    @Schema(description = "订单id")
    private Long orderId;
    @Schema(description = "商品id")
    private Long productId;
    @Schema(description = "商品名称")
    private String productName;
    @Schema(description = "购买数量")
    private Integer quantity;
    @Schema(description = "单价")
    private BigDecimal unitPrice;
    @Schema(description = "总价")
    private BigDecimal totalPrice;
    @Schema(description = "支付状态")
    private OrderStatus status;
    @Schema(description = "订单创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
