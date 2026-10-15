package com.heima.analytics.controller;


import com.heima.analytics.domain.po.WeeklyReport;
import com.heima.analytics.domain.vo.WeeklyReportVO;
import com.heima.analytics.service.IWeeklyReportService;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 宠物周报表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
@Slf4j
@Tag(name = "宠物周报接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/analytics/weekly-report")
public class WeeklyReportController {

    private final IWeeklyReportService weeklyReportService;

    @Operation(summary = "获取最新周报")
    @GetMapping("/{petId}")
    public Result<WeeklyReportVO> getNewWeeklyReport(@PathVariable Long petId) {
        log.info("获取最新周报:{}", petId);
        return weeklyReportService.getNewWeeklyReport(petId);
    }

    @Operation(summary = "手动生成/更新周报")
    @PostMapping("/{petId}")
    public Result<Long> editWeeklyReport(@PathVariable Long petId) {
        log.info("手动生成周报:{}", petId);
        return weeklyReportService.editWeeklyReport(petId);
    }

    @Operation(summary = "历史周报列表")
    @GetMapping("/{petId}/list")
    public Result<PageDTO<WeeklyReport>> historyList(@PathVariable Long petId,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size) {
        log.info("历史周报列表：{}", petId);
        return weeklyReportService.historyList(petId, page, size);
    }

}
