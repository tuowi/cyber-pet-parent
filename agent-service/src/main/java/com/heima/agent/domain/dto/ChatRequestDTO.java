package com.heima.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户发送的消息")
public class ChatRequestDTO {
    @Schema(description = "用户发送的内容",required = true)
    private String message;
}
