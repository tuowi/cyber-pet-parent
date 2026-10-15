package com.heima.shop.domain.query;

import com.heima.api.enums.InteractionType;
import com.heima.shop.annotation.AllowedInteractionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import reactor.util.annotation.Nullable;

import java.math.BigDecimal;

@Data
@Schema(description = "管理员编辑商品请求")
public class EditProductQuery {
    @Schema(description = "商品名称，1-50 字符")
    private String name;
    @Schema(description = "商品描述")
    private String description;
    @Schema(description = "图标 URL")
    private String icon;
    @Schema(description = "单价，> 0")
    private BigDecimal price;
    @Schema(description = "每人每日限购数，null=不限（未传或 <= 0 均视为 null）")
    private Integer dailyLimit;
    @Schema(description = "效果类型：FEED（喂食）/ PET（抚摸）/ BATHE（洗澡）")
//    @NotNull(message = "effectType 不能为空")
    @AllowedInteractionType  // 加上这个注解即可
    private InteractionType effectType;
    @Schema(description = "饥饿值恢复量，默认 0")
    private Integer effectHunger = 0;
    @Schema(description = "心情值恢复量，默认 0")
    private Integer effectMood = 0;
    @Schema(description = "清洁值恢复量，默认 0")
    private Integer effectCleanliness = 0;
}
