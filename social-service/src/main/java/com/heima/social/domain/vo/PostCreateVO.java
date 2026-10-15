package com.heima.social.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "帖子发布成功返回")
public class PostCreateVO {
    @Schema(description = "帖子ID")
    private Long postId;
    @Schema(description = "宠物ID")
    private Long petId;
    @Schema(description = "宠物昵称")
    private String petName;
    @Schema(description = "帖子内容")
    private String content;
    @Schema(description = "帖子图片")
    private String imageUrl;
    @Schema(description = "帖子点赞数")
    private Integer likeCount;
    @Schema(description = "帖子评论数")
    private Integer commentCount;
    @Schema(description = "帖子创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
