package com.heima.common.util;

import java.util.regex.Pattern;

/**
 * 用户名校验工具类
 */
public class UsernameValidatorUtil {
    // 正则：^[a-zA-Z0-9]{4,8}$
    // ^ 开始，[a-zA-Z0-9] 大小写字母+数字，{4,8} 重复4~8次，$ 结束
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,20}$");

    /**
     * 校验用户名是否合法
     * @param username 待校验的用户名
     * @return true-合法，false-不合法
     */
    public static boolean isValid(String username) {
        if (username == null) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }
}
