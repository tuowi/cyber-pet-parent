package com.heima.common.interceptors;

import cn.hutool.core.util.StrUtil;
import com.heima.common.util.JwtUtils;
import com.heima.common.util.UserContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.lang.Nullable;

// 拦截器想要生效需要配置类MvcConfig
public class UserInfoInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "User-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取登录用户信息
        String userInfo = request.getHeader(USER_ID_HEADER);
        // 2.判断是否获取了用户，如果有，存入ThreadLocal
        if (StrUtil.isNotBlank(userInfo)) {
            UserContextHolder.setUserId(Long.valueOf(userInfo));
        }
        // 3.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 清理用户
        UserContextHolder.clear();
    }
}
