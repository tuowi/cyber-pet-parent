package com.heima.analytics.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "我的宠物排名返回")
public class RankVO {
    @Schema(description = "当前排名")
    private Integer rank;
    @Schema(description = "总参与排行的宠物数")
    private Integer total;
    @Schema(description = "宠物id")
    private Long petId;
    @Schema(description = "宠物昵称")
    private String petName;
    @Schema(description = "亲密度值")
    private Integer intimacy;
    @Schema(description = "百分位描述")
    private String percentile;
}
