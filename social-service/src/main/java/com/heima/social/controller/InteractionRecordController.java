package com.heima.social.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.social.domain.dto.MatchRequestDTO;
import com.heima.social.domain.dto.MatchResultDTO;
import com.heima.social.domain.dto.StealRequestDTO;
import com.heima.social.domain.dto.ws.StealAlertMessage;
import com.heima.social.domain.po.InteractionRecord;
import com.heima.social.domain.vo.MatchResultVO;
import com.heima.social.domain.vo.StealResultVO;
import com.heima.social.service.IInteractionRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 社交互动记录表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Slf4j
@Tag(name = "社交互动记录接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/social/interaction-record")
public class InteractionRecordController {

    private final IInteractionRecordService interactionRecordService;

    private final SimpMessagingTemplate messagingTemplate;

    @Operation(summary = "在线心跳")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody MatchRequestDTO matchRequestDTO) {
        log.info("在线心跳：{}", matchRequestDTO);
        return interactionRecordService.heartbeat(matchRequestDTO);
    }

    @Operation(summary = "查找附近在线宠物")
    @PostMapping("/match")
    public Result<List<MatchResultVO>> match(@RequestBody MatchResultDTO matchResultDTO) {
        log.info("查找附近在线宠物:{}", matchResultDTO);
        return interactionRecordService.match(matchResultDTO);
    }

    @Operation(summary = "偷零食")
    @PostMapping("/steal")
    public Result<StealResultVO> steal(@RequestBody @Validated StealRequestDTO stealRequestDTO) {
        log.info("偷零食：{}", stealRequestDTO);
        return interactionRecordService.steal(stealRequestDTO);
    }

    // 加到 InteractionRecordController 里
    @Operation(summary = "测试websocket")
    @GetMapping("/ws-test")
    public Result<String> wsTest(@RequestParam String targetUserId) {
        StealAlertMessage testMsg = StealAlertMessage.builder()
                .type("STEAL_ALERT")
                .fromUserId(0L)
                .fromNickname("测试用户")
                .petName("测试宠物")
                .reward("测试道具")
                .message("这是一条 WebSocket 测试消息")
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(targetUserId, "/queue/steal", testMsg);
        return Result.ok("已发送测试消息给用户: " + targetUserId);
    }

    @Operation(summary = "上传在线位置")
    @PostMapping("/location")
    public Result<Void> location(@RequestBody MatchRequestDTO matchRequestDTO) {
        log.info("最新位置：{}", matchRequestDTO);
        return interactionRecordService.location(matchRequestDTO);
    }

    @Operation(summary = "互动记录")
    @GetMapping("/log")
    public Result<PageDTO<InteractionRecord>> logPageList(@RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        log.info("互动记录，page:{},size:{}", page, size);
        return interactionRecordService.logPageList(page, size);
    }
}
