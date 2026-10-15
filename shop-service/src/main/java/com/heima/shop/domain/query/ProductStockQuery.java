package com.heima.shop.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "修改库存请求（管理端）")
public class ProductStockQuery {

    @Schema(description = "新库存数量，>= 0", example = "5000")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;
}
