package com.heima.shop.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.ProductEffectDTO;
import com.heima.api.domain.dto.Result;
import com.heima.shop.domain.po.UserBackpack;
import com.heima.shop.domain.vo.UserBackpackVO;
import com.heima.shop.service.IUserBackpackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户背包表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-22
 */
@Slf4j
@Tag(name = "用户背包接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/shop/user-backpack")
public class UserBackpackController {

    private final IUserBackpackService userBackpackService;

    // 内部接口-从目标背包随机偷一个道具
    @PutMapping("/steal")
    public String stealFromBackpack(@RequestParam("targetUserId") Long targetUserId,
                             @RequestParam("thiefUserId") Long thiefUserId){
        log.info("从目标背包随机偷一个道具，返回道具名称");
        return userBackpackService.steal(targetUserId,thiefUserId);
    }

    // 内部接口-消耗背包道具（pet-service 互动时调用）
    @PutMapping("/consume")
    public ProductEffectDTO consumeItem(@RequestParam("userId") Long userId,
                                 @RequestParam("productId") Long productId) {
        log.info("消耗背包道具: userId={}, productId={}", userId, productId);
        return userBackpackService.consumeItem(userId, productId);
    }

    // 内部接口-根据用户id查询用户背包道具
    @GetMapping("/list")
    public List<UserBackpack> getBackpackList(@RequestParam("userId") Long userId){
        log.info("根据用户id查询用户背包道具:{}",userId);
        return userBackpackService.lambdaQuery()
                .eq(UserBackpack::getUserId,userId)
                .gt(UserBackpack::getQuantity,0)
                .list();
    }

    @Operation(summary = "我的背包")
    @GetMapping
    public Result<PageDTO<UserBackpackVO>> page(@RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size){
        log.info("我的背包：{},{}",page,size);
        return userBackpackService.pageList(page,size);
    }

    // 内部接口-用户注销删除背包
    @DeleteMapping("/cancelAccount")
    public void deleteUserBackpack(){
        log.info("内部接口-用户注销删除背包");
        userBackpackService.deleteUserBackpack();
    }

}
