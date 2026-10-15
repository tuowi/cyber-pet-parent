package com.heima.shop.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "用户钱包余额返回")
public class WalletVO {
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "当前余额")
    private BigDecimal balance;
    @Schema(description = "累计收入")
    private BigDecimal totalIncome;
    @Schema(description = "累计支出")
    private BigDecimal totalExpense;
}
