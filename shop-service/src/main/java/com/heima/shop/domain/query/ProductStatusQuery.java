package com.heima.shop.domain.query;

import com.heima.api.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "上下架商品请求（管理端）")
public class ProductStatusQuery {

    @Schema(description = "ON_SHELF（上架）/ OFF_SHELF（下架）", example = "OFF_SHELF")
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ON_SHELF|OFF_SHELF", message = "状态值必须为 ON_SHELF 或 OFF_SHELF")
    private ProductStatus status;
}
