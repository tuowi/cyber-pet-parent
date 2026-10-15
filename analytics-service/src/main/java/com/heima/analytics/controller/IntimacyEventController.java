package com.heima.analytics.controller;


import com.heima.analytics.domain.vo.IntimacyDetailVO;
import com.heima.analytics.domain.vo.IntimacyTopVO;
import com.heima.analytics.domain.vo.RankVO;
import com.heima.analytics.service.IIntimacyEventService;
import com.heima.api.domain.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 亲密度事件流水表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
@Slf4j
@Tag(name = "亲密度事件流水")
@RestController
@RequiredArgsConstructor
@RequestMapping("/analytics/intimacy-event")
public class IntimacyEventController {

    private final IIntimacyEventService intimacyEventService;

    @Operation(summary = "亲密度排行榜")
    @GetMapping("/leaderboard/top")
    public Result<List<IntimacyTopVO>> top(@RequestParam(defaultValue = "20") Integer limit){
        log.info("亲密度排行榜前{}名次",limit);
        return intimacyEventService.top(limit);
    }

    @Operation(summary = "我的宠物排名")
    @GetMapping("/leaderboard/rank/{petId}")
    public Result<RankVO> rank(@PathVariable Long petId){
        log.info("我的宠物排名：{}",petId);
        return intimacyEventService.rank(petId);
    }

    @Operation(summary = "宠物亲密度详情")
    @GetMapping("/{petId}")
    public Result<IntimacyDetailVO> detail(@PathVariable Long petId){
        log.info("宠物亲密度详情:{}",petId);
        return intimacyEventService.detail(petId);
    }

}
