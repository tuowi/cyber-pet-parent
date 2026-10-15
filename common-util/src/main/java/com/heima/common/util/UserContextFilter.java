package com.heima.common.util;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从请求头提取 userId 放入 ThreadLocal
 * 请求结束后自动清理，防止内存泄漏
 * 微服务之间
 */
@Slf4j
@Component
@Order(1)
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "User-Id";
    private static final String USER_ROLE_HEADER = "User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdStr = request.getHeader(USER_ID_HEADER);

        if (StrUtil.isNotBlank(userIdStr)) {
            try {
                Long userId = Long.valueOf(userIdStr);
                UserContextHolder.setUserId(userId);
                log.debug("UserContextFilter: set userId={}", userId);
            } catch (NumberFormatException e) {
                log.warn("UserContextFilter: 无法解析 userId 请求头: {}", userIdStr);
            }
        }

        String role = request.getHeader(USER_ROLE_HEADER);
        if (StrUtil.isNotBlank(role)) {
            UserContextHolder.setRole(role);
            log.debug("UserContextFilter: set role={}", role);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}
