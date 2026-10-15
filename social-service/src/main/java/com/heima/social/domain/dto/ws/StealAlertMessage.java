package com.heima.social.domain.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StealAlertMessage {
    private String type;           // 固定 "STEAL_ALERT"
    private Long fromUserId;       // 偷的人的用户ID
    private String fromNickname;   // 偷的人昵称
    private String petName;        // 发起偷取的宠物名
    private String reward;         // 偷到了什么（如"小鱼干"）
    private String message;        // 友好提示文案
    private Long timestamp;        // Unix 毫秒时间戳
}
