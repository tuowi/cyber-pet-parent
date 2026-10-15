package com.heima.common.util;

import java.util.regex.Pattern;

/**
 * 密码校验工具类
 */
public class PasswordValidatorUtil {
    // 密码：6~20位字母或数字
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9]{6,20}$");

    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
