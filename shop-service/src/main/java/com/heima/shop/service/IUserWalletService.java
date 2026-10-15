package com.heima.shop.service;

import com.heima.api.domain.dto.Result;
import com.heima.shop.domain.po.UserWallet;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.shop.domain.vo.SigninVO;
import com.heima.shop.domain.vo.WalletVO;

import java.math.BigDecimal;

/**
 * <p>
 * 用户钱包表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-26
 */
public interface IUserWalletService extends IService<UserWallet> {

    /**
     * 查询余额
     *
     * @return
     */
    Result<WalletVO> queryBalance();

    /**
     * 用户每日签到
     * @return
     */
    Result<SigninVO> signin();

    // 内部接口-用户注销删除钱包
    BigDecimal deleteUserWalletByUserId();
}
