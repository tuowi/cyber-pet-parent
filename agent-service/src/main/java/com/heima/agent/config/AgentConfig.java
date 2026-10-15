package com.heima.agent.config;

import com.heima.agent.aiservice.PetAiAgent;
import com.heima.agent.memory.RedisChatMemoryStore;
import com.heima.agent.tool.*;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AgentConfig {

    //会话记忆持久化
    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;

    // 记忆持久化写法一
    // 用 MessageWindowChatMemory 包装，这是 ChatMemoryProvider 的正确用法
    ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)
            .chatMemoryStore(redisChatMemoryStore)
            .maxMessages(40)    // 保留最近 40 条消息(20对消息)
            .build();

    // 调用工具+持久化记忆
    @Bean
    public PetAiAgent petAiAgent(ChatLanguageModel chatLanguageModel,
                                 CheckMoodTool checkMoodTool,
                                 StreamingChatLanguageModel streamingChatLanguageModel,
                                 FeedTool feedTool,
                                 PlayTool playTool,
                                 BatheTool batheTool) {
        return AiServices.builder(PetAiAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(checkMoodTool, feedTool, playTool, batheTool)
                .chatMemoryProvider(memoryProvider)
                .build();
    }

    // 记忆持久化写法二
/*    //构建ChatMemoryProvider对象
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        ChatMemoryProvider chatMemoryProvider = new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .chatMemoryStore(redisChatMemoryStore)
                        .maxMessages(10)
                        .build();
            }
        };
        return chatMemoryProvider;
    }*/
}
