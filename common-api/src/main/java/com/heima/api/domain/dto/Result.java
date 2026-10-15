package com.heima.api.domain.dto;

import com.heima.api.enums.BizErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(BizErrorCode.SUCCESS.getCode(), "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(BizErrorCode.SUCCESS.getCode(), "success", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
