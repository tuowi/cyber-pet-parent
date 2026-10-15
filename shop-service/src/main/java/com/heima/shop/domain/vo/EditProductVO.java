package com.heima.shop.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "管理员编辑商品返回")
public class EditProductVO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "更新后的商品名称")
    private String name;
    @Schema(description = "更新后的单价")
    private BigDecimal price;
    @Schema(description = "更新后的每日限购数")
    private Integer dailyLimit;
    @Schema(description = "更新后的效果类型")
    private InteractionType effectType;
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
