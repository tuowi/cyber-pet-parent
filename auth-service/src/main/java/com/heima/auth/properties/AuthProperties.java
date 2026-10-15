package com.heima.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties("cyber.auth")
public class AuthProperties {
    private List<String> excludePaths;
}
