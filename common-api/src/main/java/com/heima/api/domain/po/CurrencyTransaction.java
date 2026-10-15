package com.heima.api.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.heima.api.enums.CurrencyBizType;
import com.heima.api.enums.CurrencyTransactionType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 货币流水表
 * </p>
 *
 * @author lucy
 * @since 2026-07-26
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("currency_transaction")
public class CurrencyTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 变动金额，正=收入，负=支出
     */
    private BigDecimal amount;

    /**
     * INCOME（收入）/ EXPENSE（支出）
     */
    private CurrencyTransactionType type;

    /**
     * 业务类型: DAILY_SIGNIN / REGISTER_GIFT / ORDER_PAY
     */
    private CurrencyBizType bizType;

    /**
     * 关联业务ID（如 orderId）
     */
    private Long bizId;

    /**
     * 变动后余额（快照）
     */
    private BigDecimal balanceAfter;

    private LocalDateTime createTime;


}
