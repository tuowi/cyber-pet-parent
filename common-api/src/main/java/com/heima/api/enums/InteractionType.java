package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum InteractionType {
    FEED("喂食"),
    PET("抚摸"),
    BATHE("洗澡");

    private final String desc;

    InteractionType(String desc) {
        this.desc = desc;
    }
}
