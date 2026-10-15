package com.heima.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
//自动把配置文件里以 knowledge-platform.jwt 开头的配置，注入到当前类的字段里
@ConfigurationProperties(prefix = "cyber.jwt")
public class JwtProperties {
    private String secretKey;
    private long tokenTTL;
    private String tokenName;
}
