package com.heima.auth.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "用户登录返回")
public class LoginVO {
    @Schema(description = "用户id")
    private Long userId;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "用户昵称")
    private String nickname;
    @Schema(description = "用户头像")
    private String avatar;
    @Schema(description = "用户角色: USER / ADMIN")
    private String role;
    @Schema(description = "用户密钥")
    private String token;
    @Schema(description = "JWT Token，后续请求放入 Authorization 头")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireAt;
}
