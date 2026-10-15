package com.heima.social.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户位置")
public class MatchRequestDTO {
    @Schema(description = "纬度")
    private double lat;
    @Schema(description = "经度")
    private double lng;
}
