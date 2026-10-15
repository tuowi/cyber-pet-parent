package com.heima.shop.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.CurrencyTransactionType;
import com.heima.shop.domain.po.CurrencyTransaction;
import com.heima.shop.domain.vo.TransactionVO;
import com.heima.shop.service.ICurrencyTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 货币流水表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-26
 */
@Slf4j
@Tag(name = "货币流水接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/shop/currency-transaction")
public class CurrencyTransactionController {

    private final ICurrencyTransactionService currencyTransactionService;

    // 用户注册新增货币流水记录
    @PostMapping("/save")
    public void saveCurrencyTransaction(@RequestBody CurrencyTransaction currencyTransaction) {
        log.info("用户注册新增货币流水记录:{}", currencyTransaction);
        currencyTransactionService.save(currencyTransaction);
    }

    @Operation(summary = "货币流水明细")
    @GetMapping("/transactions")
    public Result<PageDTO<TransactionVO>> list(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        log.info("货币流水明细：page={},size={}", page, size);
        return currencyTransactionService.transactions(page, size);
    }

    // 内部接口-获取一段时间总收入
    @GetMapping("/totalEarnings")
    public BigDecimal getWeekTodayTotalEarnings(@RequestParam Long userId,
                                                @RequestParam LocalDateTime start,
                                                @RequestParam LocalDateTime end) {
        log.info("获取这段时间总收入：{}", userId);
        List<CurrencyTransaction> list = currencyTransactionService.lambdaQuery()
                .eq(CurrencyTransaction::getUserId, userId)
                .eq(CurrencyTransaction::getType, CurrencyTransactionType.INCOME)
                .ge(CurrencyTransaction::getCreateTime, start)
                .lt(CurrencyTransaction::getCreateTime, end)
                .list();

        return list.stream()
                .map(CurrencyTransaction::getAmount) // 假设 getAmount 返回 BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 内部接口-获取一段时间支出
    @GetMapping("/totalExpenses")
    public BigDecimal getWeekTodayTotalExpenses(@RequestParam Long userId,
                                                @RequestParam LocalDateTime start,
                                                @RequestParam LocalDateTime end){
        log.info("获取这段时间总支出：{}",userId);

        List<CurrencyTransaction> list = currencyTransactionService.lambdaQuery()
                .eq(CurrencyTransaction::getUserId, userId)
                .eq(CurrencyTransaction::getType, CurrencyTransactionType.EXPENSE)
                .ge(CurrencyTransaction::getCreateTime, start)
                .lt(CurrencyTransaction::getCreateTime, end)
                .list();

        // 取绝对值
        return list.stream()
                .map(CurrencyTransaction::getAmount) // 假设 getAmount 返回 BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .negate();
    }

}
