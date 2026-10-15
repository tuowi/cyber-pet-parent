package com.heima.pet.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.InteractionType;
import com.heima.pet.domain.query.InteractRequest;
import com.heima.pet.domain.vo.InteractResultVO;
import com.heima.pet.domain.vo.StateTransitionVO;
import com.heima.pet.service.IStateTransitionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 状态流转日志表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Slf4j
@Tag(name = "宠物状态接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/pet/state-transition-log")
public class StateTransitionLogController {

    private final IStateTransitionLogService stateTransitionLogService;

    @Operation(summary = "与宠物互动（喂食 / 抚摸 / 洗澡）")
    @PostMapping("/{petId}/interact")
    public Result<InteractResultVO> interact(@PathVariable Long petId,
                                            @Validated @RequestBody InteractRequest request) {
        log.info("宠物互动，宠物id：{}，互动方式：{}，道具id：{}",
                petId, request.getType() != null ? request.getType().getDesc() : "null",
                request.getProductId());
        return stateTransitionLogService.interact(petId, request.getType(), request.getProductId());
    }

    @Operation(summary = "获取状态变更历史")
    @GetMapping("/{petId}/state-log")
    public Result<PageDTO<StateTransitionVO>> logList(@PathVariable Long petId,
                                                       @RequestParam(required = false) Long page,
                                                       @RequestParam(required = false) Long size) {
        log.info("获取状态变更历史，宠物id：{}，页码：{}，页大小：{}", petId, page, size);
        return stateTransitionLogService.logList(petId, page, size);
    }

    // 内部接口-扣减目标宠物心情值 -5
    @PostMapping("/{petId}/deduct-mood")
    public boolean deductMoodById(@PathVariable("petId") @NotNull Long petId){
        log.info("扣减目标宠物心情值 -5:{}",petId);
        return stateTransitionLogService.deductMoodByPetId(petId);
    }
}
