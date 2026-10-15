package com.heima.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("social-service")
public interface SocialClient {

    // 查询所有 post_like WHERE user_id=? → 物理删除
    @DeleteMapping("/social/post-like/cancelAccount")
    public Boolean deleteLikeByUserId(@RequestParam Long userId);
}
