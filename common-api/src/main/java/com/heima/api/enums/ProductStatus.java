package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum ProductStatus {
    ON_SHELF("上架"),
    OFF_SHELF("下架");

    private final String desc;

    ProductStatus(String desc) {
        this.desc = desc;
    }
}
