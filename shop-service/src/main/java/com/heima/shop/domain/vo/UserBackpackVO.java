package com.heima.shop.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(defaultValue = "我的背包返回")
public class UserBackpackVO {
    @Schema(defaultValue = "商品id")
    private Long productId;
    @Schema(defaultValue = "商品名词")
    private String name;
    @Schema(defaultValue = "图标 URL")
    private String icon;
    @Schema(defaultValue = "道具描述")
    private String description;
    @Schema(defaultValue = "商品种类")
    private InteractionType effectType;
    @Schema(defaultValue = "商品种类中文名")
    private String effectTypeName;
    @Schema(defaultValue = "当前持有数量")
    private Integer quantity;
    @Schema(defaultValue = "首次获得时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime firstAcquired;
}
