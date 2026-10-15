package com.heima.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 雪花ID生成器
@Component
public class SnowflakeIdGenerator {
    private final Snowflake snowflake;

    // workerId 从环境变量获取，默认 1
    public SnowflakeIdGenerator(@Value("${snowflake.worker-id:1}") long workerId) {
        this.snowflake = IdUtil.getSnowflake(workerId, 1);
    }

    public long nextId() {
        return snowflake.nextId();
    }
}
