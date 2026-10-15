package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum CurrencyTransactionType {
    INCOME("收入"),
    EXPENSE("支出");

    private final String desc;

    CurrencyTransactionType(String desc) {
        this.desc = desc;
    }
}
