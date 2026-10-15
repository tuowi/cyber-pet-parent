package com.heima.api.config;

import cn.hutool.core.util.StrUtil;
import com.heima.common.util.UserContextHolder;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FeignConfig {

    private static final String USER_ID_HEADER = "User-Id";

    // openfeign日志输出
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

/*    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // 从当前请求上下文获取 JWT 并透传给下游
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String token = attributes.getRequest().getHeader("Authorization");
                if (StrUtil.isNotBlank(token)) {
                    template.header("Authorization", token);
                }
            }
        };
    }*/

    // 微服务之间传递用户信息
    @Bean
    public RequestInterceptor userInfoRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                Long userId = UserContextHolder.getUserId();
                if (userId != null) {
                    template.header(USER_ID_HEADER, userId.toString());
                }
            }
        };
    }
}
