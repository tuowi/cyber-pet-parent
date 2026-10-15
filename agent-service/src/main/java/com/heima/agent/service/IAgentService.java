package com.heima.agent.service;

import com.heima.agent.domain.vo.ChatResponseVO;
import com.heima.agent.domain.vo.MemoryVO;
import com.heima.api.domain.dto.Result;

public interface IAgentService {

    // 向宠物对话
    Result<ChatResponseVO> chat(Long petId, String message);

    /**
     * 清除会话记忆
     * @param petId
     * @return
     */
    Result<Void> clear(Long petId);

    /**
     * 获取历史对话
     * @param petId
     * @return
     */
    Result<MemoryVO> history(Long petId);
}
