package com.heima.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(defaultValue = "历史记忆返回")
public class MemoryVO {
    @Schema(defaultValue = "宠物id")
    private Long petId;
    @Schema(defaultValue = "宠物昵称")
    private String petName;
    @Schema(defaultValue = "对话内容")
    private List<Messages> messages;
    @Schema(defaultValue = "对话条数")
    private Integer total;
}
