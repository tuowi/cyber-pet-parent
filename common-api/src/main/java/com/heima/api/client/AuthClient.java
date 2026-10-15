package com.heima.api.client;

import com.heima.api.domain.po.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

// 客户端申请http请求
@FeignClient("auth-service")
public interface AuthClient {

    /**
     * 根据id批量返回用户信息
     * @param ids
     * @return
     */
    @GetMapping("/auth")
    List<UserInfo> queryUserByIds(@RequestParam("ids") Collection<Long> ids);

    // 获取用户信息
    @GetMapping("/auth/{id}")
    public UserInfo queryUserById(@PathVariable Long id);
}
