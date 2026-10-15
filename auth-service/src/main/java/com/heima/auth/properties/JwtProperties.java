package com.heima.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cyber.jwt")
public class JwtProperties {
    private String secretKey;
    private long tokenTTL;
    private String tokenName;
}
