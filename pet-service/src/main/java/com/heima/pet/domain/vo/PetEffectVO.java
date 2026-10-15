package com.heima.pet.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "互动的属性变化值")
public class PetEffectVO {
    @Schema(description = "饥饿变化值")
    private Integer hungerChange;
    @Schema(description = "心情变化值")
    private Integer moodChange;
    @Schema(description = "清洁变化值")
    private Integer cleanlinessChange;
}
