package com.heima.shop.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.BizErrorCode;
import com.heima.common.util.UserContextHolder;
import com.heima.shop.domain.po.CurrencyTransaction;
import com.heima.shop.domain.query.PageQuery;
import com.heima.shop.domain.vo.TransactionVO;
import com.heima.shop.mapper.CurrencyTransactionMapper;
import com.heima.shop.service.ICurrencyTransactionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 货币流水表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-26
 */
@Service
public class CurrencyTransactionServiceImpl extends ServiceImpl<CurrencyTransactionMapper, CurrencyTransaction> implements ICurrencyTransactionService {

    /**
     * 货币流水明细
     *
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<TransactionVO>> transactions(Integer page, Integer size) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.分页查询：SELECT * FROM currency_transaction WHERE user_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 20 : size);
        Page<CurrencyTransaction> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();

        lambdaQuery()
                .eq(CurrencyTransaction::getUserId, userId)
                .page(sort);
        // 3.遍历结果，将 type/bizType 枚举编码映射为中文名（INCOME→"收入"，DAILY_SIGNIN→"每日签到"等）
        // 3.1.类型转换
        List<CurrencyTransaction> records = sort.getRecords();
        List<TransactionVO> transactionVOS = BeanUtil.copyToList(records, TransactionVO.class);
        // 3.2.填充数据
        List<TransactionVO> list = transactionVOS.stream().peek(transactionVO -> {
            transactionVO.setTypeName(transactionVO.getType().getDesc());
            transactionVO.setBizTypeName(transactionVO.getBizType().getDesc());
        }).toList();

        // 4.返回 PageDTO
        PageDTO<TransactionVO> transactionVOPageDTO = PageDTO.of(sort, list);
        return Result.ok(transactionVOPageDTO);
    }
}
