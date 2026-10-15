package com.heima.social.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "评论请求")
public class CommentRequestDTO {
    @Schema(description = "评论的内容",required = true)
    private String content;
}
