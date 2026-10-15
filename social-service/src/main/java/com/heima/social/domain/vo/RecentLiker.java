package com.heima.social.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "点赞人信息")
public class RecentLiker {
    @Schema(description = "点赞用户 ID")
    private Long userId;
    @Schema(description = "点赞用户昵称")
    private String nickname;
}
