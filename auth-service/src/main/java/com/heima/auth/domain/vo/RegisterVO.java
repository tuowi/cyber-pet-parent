package com.heima.auth.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Schema(description = "用户注册成功返回")
public class RegisterVO {
    @Schema(description = "用户id主键")
    private Long userId;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "用户昵称")
    private String nickname;
}
