package com.heima.agent.config;

import com.heima.agent.properties.ChatModelProperties;
import com.heima.agent.properties.StreamingChatModelProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class QwenModelConfig {

    @Autowired
    private ChatModelProperties chatModelProperties;

    @Autowired
    private StreamingChatModelProperties streamingChatModelProperties;

    // @Value("${langchain4j.open-ai.chat-model.base-url}")
//    private final String baseUrl=chatModelProperties.getBaseUrl();

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .maxTokens(chatModelProperties.getMaxTokens())
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(){
        return OpenAiStreamingChatModel.builder()
                .baseUrl(streamingChatModelProperties.getBaseUrl())
                .apiKey(streamingChatModelProperties.getApiKey())
                .modelName(streamingChatModelProperties.getModelName())
                .temperature(streamingChatModelProperties.getTemperature())
                .maxTokens(streamingChatModelProperties.getMaxTokens())
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
