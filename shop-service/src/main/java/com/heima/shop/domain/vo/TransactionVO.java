package com.heima.shop.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.CurrencyBizType;
import com.heima.api.enums.CurrencyTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "货币流水返回单体")
public class TransactionVO {
    @Schema(description = "货币流水ID")
    private Long id;
    @Schema(description = "变动金额")
    private BigDecimal amount;
    @Schema(description = "流水类型")
    private CurrencyTransactionType type;
    @Schema(description = "流水类型中文名")
    private String typeName;
    @Schema(description = "业务类型")
    private CurrencyBizType bizType;
    @Schema(description = "业务类型中文名")
    private String bizTypeName;
    @Schema(description = "变动后余额")
    private BigDecimal balanceAfter;
    @Schema(description = "流水创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
