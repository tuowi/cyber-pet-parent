package com.heima.agent.memory;

import com.heima.api.constant.RedisKeyPrefix;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// 记忆存入redis
@Repository
public class RedisChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // 获取会话消息
        // 1.从 Redis List agent:memory:{userId}:{petId} 读取最近 20 条对话记录（LRANGE 0 19）
        String memoryKey = RedisKeyPrefix.AGENT_MEMORY_KEY + memoryId;
        List<String> jsonList = stringRedisTemplate.opsForList().range(memoryKey, 0, 19);
        // 2.验证是否有历史对话
        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }
        return jsonList.stream()
                .map(json -> {
                    try {
                        // 关键：使用 LangChain4j 提供的反序列化方法（支持多态）
                        return ChatMessageDeserializer.messageFromJson(json);
                    } catch (Exception e) {
                        throw new RuntimeException("反序列化消息失败", e);
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 更新会话消息
        // LLM 返回后：LPUSH 用户消息 + AI 回复到 Redis List，LTRIM 保留最近 40 条（20 对消息）
        // 1.删除旧消息
        String memoryKey = RedisKeyPrefix.AGENT_MEMORY_KEY + memoryId;
        stringRedisTemplate.delete(memoryKey);
        // 2. 如果新列表不空，序列化后批量右插（或左插，只要与 getMessages 读取顺序一致）
        if (messages != null && !messages.isEmpty()) {
            List<String> jsonList = messages.stream()
                    .map(msg -> {
                        try {
                            return ChatMessageSerializer.messageToJson(msg);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
            stringRedisTemplate.opsForList().rightPushAll(memoryKey, jsonList);

            // 4. ⭐ 关键：写入成功后，重新设置过期时间（30分钟无操作则自动清理）
            stringRedisTemplate.expire(memoryKey, Duration.ofMinutes(30));
            // 注意：这里不需要再用 LTRIM，因为 messages 已经是裁剪好的窗口大小
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 清理会话内容
        // DEL agent:memory:{userId}:{petId}
        String memoryKey = RedisKeyPrefix.AGENT_MEMORY_KEY + String.valueOf(memoryId);
        stringRedisTemplate.delete(memoryKey);
    }
}
