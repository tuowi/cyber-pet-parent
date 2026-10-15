package com.heima.shop.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NonNull;

@Data
@Schema(description = "商品下单购买请求")
public class OrderPlaceQuery {
    @Schema(description = "商品 ID")
    @NonNull
    private Long productId;
    @Schema(description = "购买数量，1-99")
    @NonNull
    private Integer quantity;
}
