package com.heima.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cyber.avatar")
@Data
public class AvatarPathProperties {
    private String authPath;
}
