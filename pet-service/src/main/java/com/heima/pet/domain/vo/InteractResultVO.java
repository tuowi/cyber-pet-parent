package com.heima.pet.domain.vo;

import com.heima.api.enums.InteractionType;
import com.heima.api.enums.PetState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "宠物互动返回")
public class InteractResultVO {
    @Schema(description = "互动类型编码")
    private InteractionType type;
    @Schema(description = "互动类型中文名")
    private String typeName;
    @Schema(description = "互动前状态")
    private PetState beforeState;
    @Schema(description = "互动后状态")
    private PetState afterState;
    @Schema(description = "状态是否发生变化")
    private boolean stateChanged;
    @Schema(description = "互动后的最新属性")
    private PetAttributeVO attributes;
    @Schema(description = "本次互动的属性变化量")
    private PetEffectVO effects;
    @Schema(description = "宠物的萌言萌语反馈")
    private String cuteMessage;
}
