package com.heima.social.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.Species;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "帖子列表返回")
public class PostVO {
    @Schema(description = "帖子id")
    private Long postId;
    @Schema(description = "用户id")
    private Long userId;
    @Schema(description = "用户昵称")
    private String nickname;
    @Schema(description = "宠物id")
    private Long petId;
    @Schema(description = "宠物昵称")
    private String petName;
    @Schema(description = "宠物种类")
    private Species species;
    @Schema(description = "帖子内容")
    private String content;
    @Schema(description = "帖子图片")
    private String imageUrl;
    @Schema(description = "帖子点赞数")
    private Integer likeCount;
    @Schema(description = "帖子评论数")
    private Integer commentCount;
    @Schema(description = "浏览者是否点赞")
    private Boolean liked;
    @Schema(description = "帖子热度")
    private Double heatScore;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
