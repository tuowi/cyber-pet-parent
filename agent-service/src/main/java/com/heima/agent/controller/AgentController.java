package com.heima.agent.controller;

import com.heima.agent.domain.dto.ChatRequestDTO;
import com.heima.agent.domain.vo.ChatResponseVO;
import com.heima.agent.domain.vo.MemoryVO;
import com.heima.agent.service.IAgentService;
import com.heima.api.domain.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "AI 宠物翻译机接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentController {

    private final IAgentService agentService;

    @Operation(summary = "与宠物对话")
    @PostMapping("/chat/{petId}")
    public Result<ChatResponseVO> chat(@PathVariable Long petId, @RequestBody ChatRequestDTO chatRequestDTO) {
        log.info("向宠物{}说出：{}", petId, chatRequestDTO.getMessage());
        return agentService.chat(petId, chatRequestDTO.getMessage());
    }

    @Operation(summary = "清除对话记忆")
    @DeleteMapping("/memory/{petId}")
    public Result<Void> clear(@PathVariable Long petId){
        log.info("清除会话记忆：{}",petId);
        return agentService.clear(petId);
    }

    @Operation(summary = "获取对话历史")
    @GetMapping("/memory/{petId}")
    public Result<MemoryVO> history(@PathVariable Long petId){
        log.info("获取历史对话：{}",petId);
        return agentService.history(petId);
    }
}
