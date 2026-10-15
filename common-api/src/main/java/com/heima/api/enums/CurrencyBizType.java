package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum CurrencyBizType {
    DAILY_SIGNIN("每日签到"),
    REGISTER_GIFT("注册礼包"),
    ORDER_PAY("订单支付"),
    ACCOUNT_DELETION("用户注销");

    private final String desc;

    CurrencyBizType(String desc) {
        this.desc = desc;
    }
}
