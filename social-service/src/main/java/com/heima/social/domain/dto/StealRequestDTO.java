package com.heima.social.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "偷零食请求")
public class StealRequestDTO {
    @Schema(description = "发起方宠物 ID（当前用户派出偷零食的宠物）")
    @NotNull
    private Long petId;
    @Schema(description = "目标用户 ID")
    @NotNull
    private Long targetUserId;
    @Schema(description = "目标宠物 ID")
    @NotNull
    private Long targetPetId;
}
