package com.heima.agent.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
//自动把配置文件里以 model.chat-model 开头的配置，注入到当前类的字段里
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
}
