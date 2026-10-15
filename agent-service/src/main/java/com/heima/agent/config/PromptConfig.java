package com.heima.agent.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 启动时把文件内容加载成 Bean
// 放在 config/ 包下
@Configuration
public class PromptConfig {

    @Bean
    public String systemPromptTemplate() throws IOException {
        Resource resource = new ClassPathResource("system-prompt.txt");
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
