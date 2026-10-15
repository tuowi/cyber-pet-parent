package com.heima.analytics.domain.vo;

import com.heima.api.enums.Species;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "紧密度排行单体返回")
public class IntimacyTopVO {
    @Schema(description = "排名")
    private Integer rank;
    @Schema(description = "用户id")
    private Long userId;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "宠物id")
    private Long petId;
    @Schema(description = "宠物昵称")
    private String petName;
    @Schema(description = "宠物种类")
    private Species species;
    @Schema(description = "亲密度")
    private Integer intimacy;
}
