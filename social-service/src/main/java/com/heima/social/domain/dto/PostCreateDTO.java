package com.heima.social.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "发布帖子请求")
public class PostCreateDTO {
    @Schema(description = "发帖的宠物 ID")
    private Long petId;
    @Schema(description = "帖子内容，1-500 字")
    private String content;
    @Schema(description = "配图 URL")
    private String imageUrl;
}
