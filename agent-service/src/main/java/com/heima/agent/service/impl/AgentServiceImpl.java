package com.heima.agent.service.impl;

import com.heima.agent.aiservice.PetAiAgent;
import com.heima.agent.domain.vo.ChatResponseVO;
import com.heima.agent.domain.vo.MemoryVO;
import com.heima.agent.domain.vo.Messages;
import com.heima.agent.domain.vo.ToolCallRecord;
import com.heima.agent.memory.RedisChatMemoryStore;
import com.heima.agent.service.IAgentService;
import com.heima.agent.utils.ToolCallContext;
import com.heima.api.client.PetClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.vo.PetDetailVO;
import com.heima.api.enums.BizErrorCode;
import com.heima.common.util.UserContextHolder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements IAgentService {

    private final PetClient petClient;

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PetAiAgent petAiAgent;

    @Autowired
    private String systemPromptTemplate;

    private final RedisChatMemoryStore memoryStore;

    /**
     * 与宠物对话
     *
     * @param petId
     * @param message
     * @return
     */
    @Override
    public Result<ChatResponseVO> chat(Long petId, String message) {
        // 消息不可为空
        if (message.isEmpty() || message == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "发送消息不可为空");
        }
        // 1.通过 Feign 调用 pet-service 校验 petId 属于当前用户（UserContextHolder），不匹配返回 403
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该宠物不存在");
        }
        if (!petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "该宠物不属于您");
        }
        // 2.构建 memoryId = "{userId}:{petId}"
        String memoryId = userId + ":" + petId;
        // 3.从 Redis List agent:memory:{userId}:{petId} 读取最近 20 条对话记录（LRANGE 0 19）
        // RedisChatMemoryStore调用chat之前自动完成
        String memoryKey = RedisKeyPrefix.AGENT_MEMORY_KEY + memoryId;
//        List<String> range = stringRedisTemplate.opsForList().range(memoryKey, 0, 19);

        // 4.通过 Feign 获取宠物信息（personality, petName, state），注入 system prompt（含性格设定）
        // 4.1. Feign 查宠物信息
        PetDetailVO detail = petClient.PetDetail(petId).getData();

        // 4.2. 把 {xxx} 全部替换为实际值
        String systemPrompt = systemPromptTemplate
                .replace("{petId}", String.valueOf(petId))
                .replace("{petName}", detail.getPetName())
                .replace("{speciesName}", detail.getSpeciesName())
                .replace("{species}", detail.getSpecies().name())
                .replace("{personality}", detail.getPersonality())
                .replace("{stateName}", detail.getStateName())
                .replace("{state}", detail.getState().name())
                .replace("{hunger}", String.valueOf(detail.getAttributes().getHunger()))
                .replace("{mood}", String.valueOf(detail.getAttributes().getMood()))
                .replace("{cleanliness}", String.valueOf(detail.getAttributes().getCleanliness()));
        // 5.调用 AiService.chat(memoryId, message)，内部 LLM 可能调用 Tools（如 CheckMoodTool, FeedTool）
        String reply;
        try {
            reply = petAiAgent.chat(memoryId, systemPrompt, message);
        } catch (Exception e) {
            return Result.fail(BizErrorCode.AGENT_CALL_FAILED.getCode(), BizErrorCode.AGENT_CALL_FAILED.getMessage());
        }
        // 6.每个 Tool 内部独立校验宠物所有权（不信任 controller 层已校验）  方法内部待完成
        // 7.LLM 返回后：LPUSH 用户消息 + AI 回复到 Redis List，LTRIM 保留最近 40 条（20 对消息）
        // 怎么组装用户消息 + AI 回复  （自动组装）
        // RedisChatMemoryStore调用chat之后自动完成

        // 8.返回 ChatResponse（reply, petState, toolCalls, memoryCount）
        ChatResponseVO chatResponseVO = new ChatResponseVO();
        chatResponseVO.setReply(reply);
        chatResponseVO.setPetState(petInfo.getState());
        // 获取工具调用列表
        List<ToolCallRecord> tools = ToolCallContext.getAndClear();
        chatResponseVO.setToolCalls(tools);
        // 查询长度
        // TODO 需要改进长度
        Long size = stringRedisTemplate.opsForList().size(memoryKey);
        int memoryCount = size != null ? size.intValue() : 0;
        chatResponseVO.setMemoryCount(memoryCount);
        return Result.ok(chatResponseVO);
    }

    /**
     * 清除会话记忆
     *
     * @param petId
     * @return
     */
    @Override
    public Result<Void> clear(Long petId) {
        // 1.通过 Feign 调用 pet-service 校验 petId 属于当前用户，不匹配返回 403
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该宠物不存在");
        }
        if (!petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "该宠物不属于您");
        }
        // 2.DEL agent:memory:{userId}:{petId}
        String memoryId = userId + ":" + petId;
        memoryStore.deleteMessages(memoryId);
        // 3.返回成功
        return Result.ok();
    }

    /**
     * 获取历史对话
     *
     * @param petId
     * @return
     */
    @Override
    public Result<MemoryVO> history(Long petId) {
        // 1.通过 Feign 调用 pet-service 校验 petId 属于当前用户，不匹配返回 403
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该宠物不存在");
        }
        if (!petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "该宠物不属于您");
        }
        // 2.LRANGE agent:memory:{userId}:{petId} 0 -1 获取所有消息（最多 40 条）
        String memoryId = userId + ":" + petId;
        String memoryKey = RedisKeyPrefix.AGENT_MEMORY_KEY + memoryId;
        List<String> jsonList = stringRedisTemplate.opsForList().range(memoryKey, 0, -1);

        // 全局定义
        MemoryVO memoryVO = new MemoryVO();
        memoryVO.setPetId(petId);
        memoryVO.setPetName(petInfo.getPetName());

        // 验证是否有历史对话
        if (jsonList == null || jsonList.isEmpty()) {
            memoryVO.setMessages(Collections.emptyList());
            memoryVO.setTotal(0);
            return Result.ok(memoryVO);
        }
        // 若有消息
        List<ChatMessage> AIMessages = jsonList.stream()
                .map(json -> {
                    try {
                        // 关键：使用 LangChain4j 提供的反序列化方法（支持多态）
                        return ChatMessageDeserializer.messageFromJson(json);
                    } catch (Exception e) {
                        throw new RuntimeException("反序列化消息失败", e);
                    }
                })
                .collect(Collectors.toList());

        List<Messages> messagesList = new ArrayList<>(AIMessages.size());
        // 3.解析每条 JSON 消息，按 role/content/time 格式化
        for (ChatMessage m : AIMessages) {
            String role = m.type().name();

            // 剔除系统提示词和工具调用
            if (role.equals("USER") || role.equals("AI")) {
                String content = m.text();

                // AI调用工具中
                if (role.equals("AI") && content == null) {
                    continue;
                }

                messagesList.add(new Messages(role, content));
            }
        }

        // 4.返回 MemoryVO（含 petId, petName, messages, total）
        memoryVO.setMessages(messagesList);
        memoryVO.setTotal(messagesList.size());
        return Result.ok(memoryVO);
    }
}
