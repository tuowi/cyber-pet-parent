package com.heima.agent.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

public interface PetAiAgent {

    // 用于聊天的接口
    @SystemMessage("{{systemPrompt}}")      // ← 整个 system prompt 动态传入
    @UserMessage("{{userMessage}}")
    String chat(@MemoryId String memoryId,
                @V("systemPrompt") String systemPrompt,   // ← 已填充好的完整提示词
                @V("userMessage") String userMessage);

    // TODO 流式调用
    @SystemMessage("{{systemPrompt}}")      // ← 整个 system prompt 动态传入
    @UserMessage("{{userMessage}}")
    Flux<String> chatStream(@MemoryId String memoryId,
                            @V("systemPrompt") String systemPrompt,   // ← 已填充好的完整提示词
                            @V("userMessage") String userMessage);
}
