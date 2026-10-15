package com.heima.social.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "点赞返回")
public class PostLikedVO {
    @Schema(description = "帖子ID")
    private Long postId;
    @Schema(description = "点赞数")
    private Integer likeCount;
}
