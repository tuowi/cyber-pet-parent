package com.heima.pet.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.PetState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "寻找离家出走的宠物返回体")
public class PetRecoverVO {
    @Schema(description = "宠物id")
    private Long petId;
    @Schema(description = "宠物昵称")
    private String petName;
    @Schema(description = "状态编码")
    private PetState state;
    @Schema(description = "状态中文名")
    private String stateName;
    @Schema(description = "离家出走时间，null=未出走")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime runawayTime;
    @Schema(description = "宠物属性值")
    private PetAttributeVO attributes;
}
