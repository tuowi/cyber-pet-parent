package com.heima.common.config;

import com.heima.common.interceptors.UserInfoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(DispatcherServlet.class)  // 微服务专有类
public class MvcConfig implements WebMvcConfigurer {

    private final String AUTH_AVATAR_PATH = "C:/Users/86157/Documents/University/avatar/auth/";

    private final String PET_AVATAR_PATH = "C:/Users/86157/Documents/University/avatar/pet/";

    private final String PRODUCT_ICON_PATH = "C:/Users/86157/Documents/University/avatar/product/";

    private final String POST_IMAGE_PATH = "C:/Users/86157/Documents/University/avatar/post/";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceptor());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/avatars/auth/**")
                .addResourceLocations("file:" + AUTH_AVATAR_PATH);

        registry.addResourceHandler("/avatars/pet/**")
                .addResourceLocations("file:" + PET_AVATAR_PATH);

        registry.addResourceHandler("/avatars/shop/**")
                .addResourceLocations("file:" + PRODUCT_ICON_PATH);

        registry.addResourceHandler("/avatars/social/**")
                .addResourceLocations("file:" + POST_IMAGE_PATH);
    }
}
