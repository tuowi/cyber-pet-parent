package com.heima.pet.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "宠物详情返回")
public class PetDetailVO extends PetVO {
    @Schema(description = "头像URL")
    private String avatar;
}
