package com.heima.auth.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "注销用户的返回")
public class CancelAccountVO {
    @Schema(description = "释放的宠物数量")
    private Integer deletedPetCount;
    @Schema(description = "清零的余额（虚拟币，不可退款）")
    private BigDecimal forfeitedBalance;
    @Schema(description = "注销时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deletedAt;
}
