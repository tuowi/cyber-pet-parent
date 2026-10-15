package com.heima.social.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.enums.Species;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "帖子详细返回")
public class PostDetailVO {
    @Schema(description = "帖子 ID")
    private Long postId;
    @Schema(description = "帖子作者用户 ID")
    private Long userId;
    @Schema(description = "帖子作者昵称")
    private String nickname;
    @Schema(description = "帖子作者头像 URL")
    private String avatar;
    @Schema(description = "发帖宠物 ID")
    private Long petId;
    @Schema(description = "宠物昵称")
    private String petName;
    @Schema(description = "物种编码")
    private Species species;
    @Schema(description = "物种中文名")
    private String speciesName;
    @Schema(description = "帖子内容")
    private String content;
    @Schema(description = "配图 URL，null=无图")
    private String imageUrl;
    @Schema(description = "点赞总数")
    private Integer likeCount;
    @Schema(description = "评论总数")
    private Integer commentCount;
    @Schema(description = "当前用户是否已点赞")
    private Boolean liked;
    @Schema(description = "热度分")
    private Double heatScore;
    @Schema(description = "发布时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @Schema(description = "最近点赞用户列表（最多 5 人），按点赞时间正序")
    private List<RecentLiker> recentLikers;
    @Schema(description = "嵌套的评论分页数据")
    private PageDTO<CommentVO> comments;
}
