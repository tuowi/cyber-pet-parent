package com.heima.social.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(defaultValue = "发表评论返回")
public class CommentVO {
    @Schema(defaultValue = "评论 ID")
    private Long commentId;
    @Schema(defaultValue = "所属帖子 ID")
    private Long postId;
    @Schema(defaultValue = "评论用户 ID")
    private Long userId;
    @Schema(defaultValue = "评论用户昵称")
    private String nickname;
    @Schema(defaultValue = "评论内容")
    private String content;
    @Schema(defaultValue = "评论时间")
    private LocalDateTime createTime;
}
