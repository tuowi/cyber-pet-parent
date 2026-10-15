package com.heima.shop.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.heima.api.enums.InteractionType;

import java.math.BigDecimal;

@Data
@Schema(description = "新增商品请求（管理端）")
public class ProductCreateQuery {

    @Schema(description = "商品名称", example = "小鱼干")
    @NotBlank(message = "商品名称不能为空")
    @Size(min = 1, max = 50, message = "商品名称 1-50 位")
    private String name;

    @Schema(description = "商品描述", example = "猫咪最爱的零食")
    private String description;

    @Schema(description = "图标 URL")
    private String icon;

    @Schema(description = "单价（虚拟币），> 0", example = "5.00")
    @DecimalMin(value = "0.01", message = "单价必须大于 0")
    private BigDecimal price;

    @Schema(description = "初始库存，>= 0", example = "2000")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    @Schema(description = "每人每日限购数，null=不限", example = "5")
    private Integer dailyLimit;

    @Schema(description = "道具分类: FEED（食物）/PET（玩具）/BATHE（洗浴），null=纯装饰品")
    @Nullable
    private InteractionType effectType;

    @Schema(description = "使用后饥饿值变化量", example = "30")
    private Integer effectHunger;

    @Schema(description = "使用后心情值变化量", example = "5")
    private Integer effectMood;

    @Schema(description = "使用后清洁值变化量", example = "0")
    private Integer effectCleanliness;
}
