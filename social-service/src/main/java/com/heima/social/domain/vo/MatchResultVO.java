package com.heima.social.domain.vo;

import com.heima.api.enums.Personality;
import com.heima.api.enums.PetState;
import com.heima.api.enums.Species;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "附近在线宠物返回")
public class MatchResultVO {
    @Schema(description = "用户id")
    private Long userId;
    @Schema(description = "用户昵称")
    private String nickname;
    @Schema(description = "宠物id")
    private Long petId;
    @Schema(description = "宠物名")
    private String petName;
    @Schema(description = "宠物种类")
    private Species species;
    @Schema(description = "宠物种类中文名")
    private String speciesName;
    @Schema(description = "宠物性格")
    private Personality personality;
    @Schema(description = "宠物状态")
    private PetState state;
    @Schema(description = "距离")
    private Double distance;
}
