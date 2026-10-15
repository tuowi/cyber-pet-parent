package com.heima.shop.domain.vo;

import com.heima.shop.domain.po.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "商品详细返回")
public class ProductDetailVO extends Product {
    @Schema(description = "今日已购买数量")
    private Integer todayBought;
    @Schema(description = "今日还可购买数量（dailyLimit 为 null 时也为 null）")
    private Integer todayRemaining;
}
