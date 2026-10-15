package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum Species {
    CAT("猫"),
    DOG("狗");

    private final String desc;

    Species(String desc) {
        this.desc = desc;
    }
}
