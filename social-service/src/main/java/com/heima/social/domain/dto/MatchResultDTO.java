package com.heima.social.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "寻找附近宠物请求")
public class MatchResultDTO {
    @Schema(description = "当前纬度")
    private double lat;
    @Schema(description = "当前经度")
    private double lng;
    @Schema(description = "搜索半径（公里）")
    private int radius;
}
