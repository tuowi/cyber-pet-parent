package com.heima.shop.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cyber.icon")
@Data
public class IconPathProperties {
    private String productPath;
}
