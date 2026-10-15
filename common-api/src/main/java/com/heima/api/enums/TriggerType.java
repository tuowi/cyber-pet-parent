package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum TriggerType {
    DECAY("自然衰减"),
    INTERACT("互动"),
    TIMEOUT("超时/到期"),
    MANUAL("手动/人工");

    private final String desc;

    TriggerType(String desc) {
        this.desc = desc;
    }
}
