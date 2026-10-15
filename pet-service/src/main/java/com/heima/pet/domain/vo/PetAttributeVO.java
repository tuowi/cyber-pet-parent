package com.heima.pet.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "宠物属性信息返回")
public class PetAttributeVO {
    @Schema(description = "饥饿值")
    private Integer hunger;
    @Schema(description = "心情值")
    private Integer mood;
    @Schema(description = "清洁值")
    private Integer cleanliness;
}
