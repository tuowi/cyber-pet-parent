package com.heima.agent.domain.vo;

import com.heima.api.enums.PetState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(defaultValue = "宠物对话返回")
public class ChatResponseVO {
    @Schema(defaultValue = "Agent 的回复（含萌语翻译）")
    private String reply;
    @Schema(defaultValue = "当前宠物状态")
    private PetState petState;
    @Schema(defaultValue = "本次对话中调用的 Tool 列表")
    private List<ToolCallRecord> toolCalls;
    @Schema(defaultValue = "当前对话记忆条数")
    private Integer memoryCount;
}
