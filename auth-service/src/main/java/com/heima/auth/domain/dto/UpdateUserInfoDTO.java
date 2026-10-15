package com.heima.auth.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "编辑用户信息请求")
public class UpdateUserInfoDTO {
    @Schema(description = "新昵称，1-20个字符")
    private String nickname;
}
