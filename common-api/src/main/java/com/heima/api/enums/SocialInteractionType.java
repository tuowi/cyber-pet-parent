package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum SocialInteractionType {
    STEAL_SNACK("偷零食"),
    PLAY_MATCH("玩游戏"),
    VISIT("拜访");

    private final String desc;

    SocialInteractionType(String desc) {
        this.desc = desc;
    }
}
