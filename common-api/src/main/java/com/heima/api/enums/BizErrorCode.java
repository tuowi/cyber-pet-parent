package com.heima.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BizErrorCode {

    // ========== 2xx 成功 ==========
    SUCCESS(HttpStatus.OK, 200, "成功"),

    // ========== 4xx 客户端错误 ==========
    BAD_REQUEST(HttpStatus.BAD_REQUEST, 400, "通用业务错误"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 401, "认证失败"),
    FORBIDDEN(HttpStatus.FORBIDDEN, 403, "权限不足"),
    NOT_FOUND(HttpStatus.NOT_FOUND, 404, "资源不存在"),
    CONFLICT(HttpStatus.CONFLICT, 409, "冲突"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, 429, "被限流"),

    // ========== 4xxxx 具体业务错误 ==========
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, 40001, "库存不足"),
    EXCEED_PURCHASE_LIMIT(HttpStatus.BAD_REQUEST, 40002, "超过限购"),
    PET_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, 40003, "宠物已达上限"),
    PET_STATUS_ABNORMAL(HttpStatus.BAD_REQUEST, 40004, "宠物状态异常"),
    DUPLICATE_OPERATION(HttpStatus.CONFLICT, 40005, "重复操作"),
    TARGET_OFFLINE(HttpStatus.BAD_REQUEST, 40006, "对方不在线"),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, 40007, "余额不足"),

    // ========== 5xxxx 服务端错误 ==========
    AGENT_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 50001, "Agent 调用失败"),
    LOCK_ACQUISITION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 50002, "分布式锁获取失败"),
    ;

    // 关联的 HTTP 状态码（用于设置 ResponseEntity）
    private final HttpStatus httpStatus;
    // 业务状态码（对应 Result.code）
    private final int code;
    // 提示信息（对应 Result.msg）
    private final String message;

    /**
     * 根据业务 code 反向查找枚举（用于特殊反序列化场景）
     */
    public static BizErrorCode fromCode(int code) {
        for (BizErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        // 找不到时默认返回通用错误，避免 NPE
        return BAD_REQUEST;
    }
}
