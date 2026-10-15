package com.heima.shop.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.shop.domain.query.OrderPlaceQuery;
import com.heima.shop.domain.vo.OrderListVO;
import com.heima.shop.domain.vo.OrderVO;
import com.heima.shop.service.IShopOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 订单表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
@Slf4j
@Tag(name = "订单管理接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/shop/shop-order")
public class ShopOrderController {

    private final IShopOrderService shopOrderService;

    @Operation(summary = "下单购买")
    @PostMapping
    public Result<OrderVO> placeOrder(@RequestBody @Validated OrderPlaceQuery query) {
        log.info("下单购买，商品id：{}，购买数量：{}", query.getProductId(), query.getQuantity());
        return shopOrderService.place(query);
    }

    @Operation(summary = "我的订单列表")
    @GetMapping("/list")
    public Result<PageDTO<OrderListVO>> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        log.info("我的订单列表，页码：{}，页大小：{}", page, size);
        return shopOrderService.orderList(page, size);
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderId}")
    public Result<OrderListVO> detailOrder(@PathVariable Long orderId) {
        log.info("订单详情，订单ID：{}", orderId);
        return shopOrderService.detailOrder(orderId);
    }
}
