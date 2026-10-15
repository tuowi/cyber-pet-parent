package com.heima.social.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cyber.image")
@Data
public class ImagePathProperties {
    private String postPath;
}
