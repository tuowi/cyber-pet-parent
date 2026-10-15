package com.heima.pet.domain.query;

import com.heima.api.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import reactor.util.annotation.Nullable;

/**
 * 宠物互动请求
 */
@Data
@Schema(description = "宠物互动请求")
public class InteractRequest {

    @Schema(description = "互动类型: FEED（喂食）/ PET（抚摸）/ BATHE（洗澡）", example = "FEED")
    private InteractionType type;

    @Schema(description = "使用的道具ID。FEED/BATHE 必传；PET 可选（不传则免费抚摸）",required = false)
    @Nullable
    private Long productId;
}
