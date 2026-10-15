package com.heima.social.listener;

import com.heima.social.domain.dto.ws.MatchRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 匹配/玩耍请求事件监听器 —— 把业务事件转为 WebSocket 推送
 *
 * <h3>和 WebSocketEventListener 的区别</h3>
 * <pre>
 * WebSocketEventListener     关注连接生命周期（上线/离线/断连）
 *                              在 config/ 包，属于基础设施层
 *
 * MatchEventListener         关注业务事件（某人想和你的宠物玩耍）
 *                              在 listener/ 包，属于业务层
 * </pre>
 *
 * <h3>使用方式</h3>
 * 后续 MatchService 中可以发布 Spring ApplicationEvent：
 * <pre>
 * applicationEventPublisher.publishEvent(new MatchRequestEvent(fromUserId, toUserId, ...));
 * </pre>
 * 本类监听到事件后，通过 SimpMessagingTemplate 推送给目标用户的 /queue/match。
 *
 * <h3>消息格式</h3>
 * 对应接口文档 8.3 中 /queue/match 的格式：
 * <pre>
 * { type: "MATCH_REQUEST", fromUserId, fromNickname, petName, message, timestamp }
 * </pre>
 *
 * @author lucy
 * @since 2026-07-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 监听匹配请求事件，推 WS 通知给目标用户
     * <p>
     * TODO: 等 MatchRequestEvent 创建后去掉注释，启用此方法
     *
     * @param event 匹配请求事件（需要创建 MatchRequestEvent 类）
     */
    // @EventListener
    // public void onMatchRequest(MatchRequestEvent event) {
    //     MatchRequestMessage msg = MatchRequestMessage.builder()
    //             .type("MATCH_REQUEST")
    //             .fromUserId(event.getFromUserId())
    //             .fromNickname(event.getFromNickname())
    //             .petName(event.getPetName())
    //             .message(event.getFromNickname() + "的" + event.getPetName() + "想和你的宠物一起玩耍！")
    //             .timestamp(System.currentTimeMillis())
    //             .build();
    //
    //     messagingTemplate.convertAndSendToUser(
    //             String.valueOf(event.getToUserId()),
    //             "/queue/match",
    //             msg
    //     );
    //     log.info("匹配请求已推送: from={} to={}", event.getFromUserId(), event.getToUserId());
    // }
}
