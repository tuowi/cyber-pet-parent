package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum EventType {
    FEED("喂食"),
    PLAY("玩耍"),
    BATHE("洗澡"),
    STEALED("被偷零食"),
    STATE_CHANGE("状态变化");

    private final String desc;

    EventType(String desc) {
        this.desc = desc;
    }
}
