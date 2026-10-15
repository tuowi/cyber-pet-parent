package com.heima.analytics.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "最新周报返回")
public class WeeklyReportVO {
    @Schema(description = "周报id")
    private Long reportId;
    @Schema(description = "宠物id")
    private Long petId;
    @Schema(description = "宠物昵称")
    private String petName;
    @Schema(description = "报告周")
    private String reportWeek;
    @Schema(description = "报告事件段")
    private String weekRange;
    @Schema(description = "本周成就")
    private WeeklyReportStatus status;
    @Schema(description = "简要总结")
    private String summary;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
