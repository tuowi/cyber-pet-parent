package com.heima.pet.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.Personality;
import com.heima.api.enums.PetState;
import com.heima.api.enums.Species;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "宠物领养返回")
public class PetVO {
    @Schema(description = "宠物id")
    private Long petId;
    @Schema(description = "宠物名称")
    private String petName;
    @Schema(description = "物种编号")
    private Species species;
    @Schema(description = "物种中文名")
    private String speciesName;
    @Schema(description = "随机性格")
    private String personality;
    @Schema(description = "状态编码")
    private PetState state;
    @Schema(description = "状态中文名")
    private String stateName;
    @Schema(description = "离家出走时间，null=未出走")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime runawayTime;
    @Schema(description = "领养时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @Schema(description = "宠物属性值")
    private PetAttributeVO attributes;
}
