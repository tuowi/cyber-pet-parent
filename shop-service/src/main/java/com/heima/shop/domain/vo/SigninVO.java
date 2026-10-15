package com.heima.shop.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "每日签到返回")
public class SigninVO {
    @Schema(description = "本次签到获得的金币数（50-100 随机）")
    private Integer reward;
    @Schema(description = "签到后余额")
    private BigDecimal balance;
    @Schema(description = "连续签到天数")
    private Integer signedDays;
}
