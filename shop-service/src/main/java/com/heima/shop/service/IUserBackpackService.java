package com.heima.shop.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.ProductEffectDTO;
import com.heima.api.domain.dto.Result;
import com.heima.shop.domain.po.UserBackpack;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.shop.domain.vo.UserBackpackVO;

/**
 * <p>
 * 用户背包表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-22
 */
public interface IUserBackpackService extends IService<UserBackpack> {

    /**
     * 从目标背包随机偷一个道具，返回道具名称
     * @param targetUserId
     * @param thiefUserId
     * @return
     */
    String steal(Long targetUserId, Long thiefUserId);

    /**
     * 消耗背包道具（互动时调用），扣减 1 个，返回道具效果值。
     * 若背包中没有该道具（quantity <= 0），返回 null。
     *
     * @param userId    用户 ID
     * @param productId 道具 ID
     * @return 道具效果 DTO，背包无该道具时返回 null
     */
    ProductEffectDTO consumeItem(Long userId, Long productId);

    /**
     * 我的背包
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<UserBackpackVO>> pageList(Integer page, Integer size);

    // 内部接口-用户注销删除背包
    void deleteUserBackpack();
}
