package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum PetState {
    NORMAL("正常"),
    HUNGRY("饥饿"),
    ANGRY("生气"),
    SICK("生病"),
    RUNAWAY("离家出走");

    private final String desc;

    PetState(String desc) {
        this.desc = desc;
    }

    // values() 的顺序就是状态优先级（从低到高）
    // RUNAWAY > SICK > ANGRY > HUNGRY > NORMAL
}