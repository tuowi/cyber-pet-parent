package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum Role {
    USER("普通用户"),
    ADMIN("管理员");

    private final String desc;

    Role(String desc) {
        this.desc = desc;
    }
}
