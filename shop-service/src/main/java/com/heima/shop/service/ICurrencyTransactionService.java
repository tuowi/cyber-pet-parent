package com.heima.shop.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.shop.domain.po.CurrencyTransaction;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.shop.domain.vo.TransactionVO;

/**
 * <p>
 * 货币流水表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-26
 */
public interface ICurrencyTransactionService extends IService<CurrencyTransaction> {

    /**
     * 货币流水明细
     * @param page
     * @param size
     * @return
     */
    Result<PageDTO<TransactionVO>> transactions(Integer page, Integer size);
}
