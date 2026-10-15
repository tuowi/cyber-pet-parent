package com.heima.api.enums;

import lombok.Getter;

@Getter
public enum WSType {
    STEAL_ALERT("被偷通知"),
    MATCH_REQUEST("匹配请求"),
    ANOMALY_ALERT("异常告警"),
    HOT_POST("热门更新");

    private final String desc;

    WSType(String desc) {
        this.desc = desc;
    }
}
