package com.heima.social.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "偷零食返回")
public class StealResultVO {
    @Schema(description = "发起方宠物 ID")
    private Long petId;
    @Schema(description = "发起方宠物昵称")
    private String petName;
    @Schema(description = "被偷宠物 ID")
    private Long targetPetId;
    @Schema(description = "被偷宠物昵称")
    private String targetPetName;
    @Schema(description = "偷到的道具名称（从目标用户背包中随机抽取，见实现步骤）")
    private String reward;
    @Schema(description = "对方宠物心情变化")
    private Integer moodChange;
    @Schema(description = "对方宠物的反应")
    private String cuteMessage;
}
