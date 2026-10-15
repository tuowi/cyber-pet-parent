package com.heima.shop.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.shop.domain.po.ShopOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.shop.domain.query.OrderPlaceQuery;
import com.heima.shop.domain.vo.OrderListVO;
import com.heima.shop.domain.vo.OrderVO;

/**
 * <p>
 * 订单表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
public interface IShopOrderService extends IService<ShopOrder> {

    /**
     * 下单购买
     * @param query
     * @return
     */
    Result<OrderVO> place(OrderPlaceQuery query);

    /**
     * 获取订单列表
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<OrderListVO>> orderList(Integer page, Integer size);

    /**
     * 订单详情
     * @param orderId
     * @return
     */
    Result<OrderListVO> detailOrder(Long orderId);
}
