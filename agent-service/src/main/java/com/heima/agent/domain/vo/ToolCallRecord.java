package com.heima.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(defaultValue = "调用的工具")
public class ToolCallRecord {
    @Schema(defaultValue = "Tool 名称")
    private String tool;
    @Schema(defaultValue = "Tool 执行摘要")
    private String summary;
}
