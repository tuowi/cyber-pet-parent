package com.heima.shop.controller;


import com.heima.api.domain.dto.Result;
import com.heima.shop.domain.po.UserWallet;
import com.heima.shop.domain.vo.SigninVO;
import com.heima.shop.domain.vo.WalletVO;
import com.heima.shop.service.IUserWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * <p>
 * 用户钱包表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-26
 */
@Slf4j
@Tag(name = "用户钱包接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/shop/user-wallet")
public class UserWalletController {

    private final IUserWalletService userWalletService;

    // 用户注册新增定义用户钱包
    @PostMapping("/save")
    public void saveUserWallet(@RequestBody UserWallet userWallet) {
        log.info("用户注册定义用户钱包:{}", userWallet);
        userWalletService.save(userWallet);
    }

    @Operation(summary = "查询余额")
    @GetMapping
    public Result<WalletVO> queryBalance() {
        log.info("查询余额");
        return userWalletService.queryBalance();
    }

    @Operation(summary = "每日签到")
    @PostMapping("/signin")
    public Result<SigninVO> signin() {
        log.info("每日签到");
        return userWalletService.signin();
    }

    // 内部接口-用户注销删除钱包
    @DeleteMapping("/cancelAccount")
    BigDecimal deleteUserWalletByUserId(){
        log.info("用户注销删除钱包");
        return userWalletService.deleteUserWalletByUserId();
    }
}
