package com.heima.api.enums;

public enum OrderStatus {
    PAID("已支付"),
    CANCELLED("已取消"),
    REFUNDED("已退款");

    private final String desc;

    OrderStatus(String desc) {
        this.desc = desc;
    }
}
