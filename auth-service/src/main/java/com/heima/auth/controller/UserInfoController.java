package com.heima.auth.controller;


import com.heima.api.domain.dto.Result;
import com.heima.auth.domain.dto.CancelAccountDTO;
import com.heima.auth.domain.dto.LoginDTO;
import com.heima.auth.domain.dto.RegisterDTO;
import com.heima.auth.domain.dto.UpdateUserInfoDTO;
import com.heima.auth.domain.po.UserInfo;
import com.heima.auth.domain.vo.CancelAccountVO;
import com.heima.auth.domain.vo.LoginVO;
import com.heima.auth.domain.vo.RegisterVO;
import com.heima.auth.domain.vo.EditAdminVO;
import com.heima.auth.domain.vo.UpdateUserInfoVO;
import com.heima.auth.service.IUserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Slf4j
@Tag(name = "用户管理接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserInfoController {

    private final IUserInfoService userInfoService;

    private static final String USER_JTI_HEADER = "User-Jti";

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<RegisterVO> register(@RequestBody @Validated RegisterDTO registerDTO) {
        log.info("用户注册:{}", registerDTO.getUsername());
        return userInfoService.register(registerDTO);
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        log.info("用户登录：{}", loginDTO.getUsername());
        return userInfoService.login(loginDTO);
    }

    // jti 完全不一样——只有退出登录这一个接口需要它。
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        log.info("退出登录");
        String jti = request.getHeader(USER_JTI_HEADER);
        return userInfoService.logout(jti);
    }

    @Operation(summary = "注销账号")
    @DeleteMapping("/account")
    public Result<CancelAccountVO> cancel(@Validated @RequestBody CancelAccountDTO cancelAccountDTO) {
        log.info("注销账号：{}", cancelAccountDTO);
        return userInfoService.cancel(cancelAccountDTO);
    }

    @Operation(summary = "设置管理员(管理端)")
    @PutMapping("/admin/promote")
    public Result<EditAdminVO> admin(@RequestParam Long userId){
        log.info("设置管理员：{}",userId);
        return userInfoService.admin(userId);
    }

    @Operation(summary = "取消管理员(管理端)")
    @PutMapping("/admin/demote")
    public Result<EditAdminVO> demoteAdmin(@RequestParam Long userId){
        log.info("取消管理员：{}",userId);
        return userInfoService.demoteAdmin(userId);
    }

    @Operation(summary = "编辑用户信息")
    @PutMapping("/user/profile")
    public Result<UpdateUserInfoVO> updateUserInfo(@Validated @RequestBody UpdateUserInfoDTO updateUserInfoDTO) {
        log.info("编辑用户信息");
        return userInfoService.updateUserInfo(updateUserInfoDTO);
    }

    // 内部接口-批量获取用户信息
    @GetMapping
    public List<UserInfo> queryUserByIds(@RequestParam("ids") List<Long> ids) {
        log.info("批量获取用户信息：{}", ids);
        return userInfoService.queryUserByIds(ids);
    }

    // 内部接口-获取用户信息
    @GetMapping("/{id}")
    public UserInfo queryUserById(@PathVariable Long id) {
        log.info("获取用户信息：{}", id);
        return userInfoService.getById(id);
    }
}
