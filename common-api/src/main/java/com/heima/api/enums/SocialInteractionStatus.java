package com.heima.api.enums;

public enum SocialInteractionStatus {
    SUCCESS("成功"),
    FAILED("失败");

    private final String desc;

    SocialInteractionStatus(String desc) {
        this.desc = desc;
    }
}
