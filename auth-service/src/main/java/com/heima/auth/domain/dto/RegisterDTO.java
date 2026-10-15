package com.heima.auth.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "用户注册请求")
public class RegisterDTO {
    @Schema(description = "用户名")
    @NotNull(message = "用户名不可为空")
    private String username;
    @Schema(description = "密码")
    @NotNull(message = "密码不可为空")
    private String password;
    @Schema(description = "昵称，若为空默认取用户名")
    private String nickname;
}
