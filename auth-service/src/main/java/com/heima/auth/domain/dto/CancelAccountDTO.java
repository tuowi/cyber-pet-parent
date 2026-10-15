package com.heima.auth.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import reactor.util.annotation.Nullable;

@Data
@Schema(description = "注销账号的请求")
public class CancelAccountDTO {
    @Schema(description = "当前密码，用于二次身份验证")
    @NotNull
    private String password;
    @Schema(description = "确认文案，必须完整匹配（防止误操作）")
    @Nullable
    private String confirm = "我已知晓：注销后所有宠物将被释放，钱包余额清零，数据不可恢复";
}
