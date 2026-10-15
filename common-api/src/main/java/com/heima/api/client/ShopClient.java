package com.heima.api.client;

import com.heima.api.domain.dto.ProductEffectDTO;
import com.heima.api.domain.po.CurrencyTransaction;
import com.heima.api.domain.po.Product;
import com.heima.api.domain.po.UserBackpack;
import com.heima.api.domain.po.UserWallet;
import com.heima.api.enums.InteractionType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@FeignClient("shop-service")
public interface ShopClient {
    // 从目标背包随机偷一个道具，返回道具名称
    @PutMapping("/shop/user-backpack/steal")
    String stealFromBackpack(@RequestParam("targetUserId") Long targetUserId,
                             @RequestParam("thiefUserId") Long thiefUserId);

    /**
     * 消耗背包道具（互动时调用），扣减 1 个，返回道具效果值
     *
     * @param userId    用户ID
     * @param productId 道具ID
     * @return 道具效果（effectType + 各属性变化值 + 道具名）
     */
    @PutMapping("/shop/user-backpack/consume")
    ProductEffectDTO consumeItem(@RequestParam("userId") Long userId,
                                 @RequestParam("productId") Long productId);

    // 用户注册新增定义用户钱包
    @PostMapping("/shop/user-wallet/save")
    void saveUserWallet(@RequestBody UserWallet userWallet);

    // 用户注册新增货币流水记录
    @PostMapping("/shop/currency-transaction/save")
    void saveCurrencyTransaction(@RequestBody CurrencyTransaction currencyTransaction);

    // 获取一段时间收入
    @GetMapping("/shop/currency-transaction/totalEarnings")
    BigDecimal getWeekTodayTotalEarnings(@RequestParam Long userId,
                                         @RequestParam LocalDateTime start,
                                         @RequestParam LocalDateTime end);

    // 获取一段时间支出
    @GetMapping("/shop/currency-transaction/totalExpenses")
    BigDecimal getWeekTodayTotalExpenses(@RequestParam Long userId,
                                         @RequestParam LocalDateTime start,
                                         @RequestParam LocalDateTime end);

    // 根据用户id查询用户背包道具
    @GetMapping("/shop/user-backpack/list")
    List<UserBackpack> getBackpackList(@RequestParam("userId") Long userId);

    // 根据商品名称进行模糊搜索，搜索到最匹配的商品并返回
    @GetMapping("/shop/product/like/productName")
    Product getProductLikeProductName(@RequestParam("productName") String productName,
                                      @RequestParam("type") InteractionType type);

    // 根据商品id列表返回商品列表
    @GetMapping("/shop/product/list/ids")
    List<Product> getProductListByIds(@RequestParam List<Long> ids);

    // 用户注销删除钱包
    @DeleteMapping("/shop/user-wallet/cancelAccount")
    BigDecimal deleteUserWalletByUserId();

    // 用户注销删除背包
    @DeleteMapping("/shop/user-backpack/cancelAccount")
    void deleteUserBackpack();
}

