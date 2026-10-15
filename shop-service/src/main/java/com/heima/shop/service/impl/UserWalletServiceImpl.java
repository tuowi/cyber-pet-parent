package com.heima.shop.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.CurrencyBizType;
import com.heima.api.enums.CurrencyTransactionType;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.shop.domain.po.CurrencyTransaction;
import com.heima.shop.domain.po.UserWallet;
import com.heima.shop.domain.vo.SigninVO;
import com.heima.shop.domain.vo.WalletVO;
import com.heima.shop.mapper.UserWalletMapper;
import com.heima.shop.service.ICurrencyTransactionService;
import com.heima.shop.service.IUserWalletService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>
 * 用户钱包表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-26
 */
@Service
@RequiredArgsConstructor
public class UserWalletServiceImpl extends ServiceImpl<UserWalletMapper, UserWallet> implements IUserWalletService {

    private final StringRedisTemplate stringRedisTemplate;

    private final ICurrencyTransactionService currencyTransactionService;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 查询余额
     *
     * @return
     */
    @Override
    public Result<WalletVO> queryBalance() {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.优先从 Redis 读取余额：GET user:balance:{userId}
        String balanceKey = RedisKeyPrefix.USER_BALANCE_KEY + userId;
        String balanceStr = stringRedisTemplate.opsForValue().get(balanceKey);
        BigDecimal balance;
        UserWallet userWallet = lambdaQuery()
                .eq(UserWallet::getUserId, userId)
                .one();
        // 3.Redis 命中 → 直接返回；未命中 → 查询 DB user_wallet 表，并回写 Redis（无 TTL，由定时任务同步）
        if (StrUtil.isEmpty(balanceStr)) {
            // 3.1.未命中 → 查询 DB user_wallet 表，并回写 Redis（无 TTL，由定时任务同步）
            // 定时任务添加余额缓存写回数据库(已添加)
            balance = userWallet.getBalance();
            // 写回redis
            stringRedisTemplate.opsForValue().set(balanceKey, String.valueOf(balance));
        } else {
            // 3.2.Redis 命中 → 直接返回
            balance = new BigDecimal(balanceStr);
        }
        // 4.返回 WalletVO（userId, balance, totalIncome, totalExpense）
        WalletVO walletVO = WalletVO.builder()
                .userId(userId)
                .balance(balance)
                .totalIncome(userWallet.getTotalIncome())
                .totalExpense(userWallet.getTotalExpense())
                .build();
        return Result.ok(walletVO);
    }

    /**
     * 用户每日签到
     * 使用位图
     *
     * @return
     */
    @Transactional
    @Override
    public Result<SigninVO> signin() {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.计算当天的 year 和 dayOfYear（LocalDate.now().getYear() / getDayOfYear()，从 1 开始
        int year = LocalDate.now().getYear();
        int dayOfYear = LocalDate.now().getDayOfYear();
        // 3.检查今日是否已签到：GETBIT signin:{userId}:{year} {dayOfYear - 1}，返回 1 则返回 400 "今天已经签到过了"
        String signinKey = RedisKeyPrefix.SIGNIN_KEY + userId + ":" + year;
        Boolean signedIn = stringRedisTemplate.opsForValue().getBit(signinKey, dayOfYear - 1);
        if (Boolean.TRUE.equals(signedIn)) {
            // 返回 400 "今天已经签到过了，明天再来吧~"
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "今天已经签到过了，明天再来吧~");
        }
        // 4.生成随机奖励（50-100）：ThreadLocalRandom.current().nextInt(51) + 50
        int reward = ThreadLocalRandom.current().nextInt(51) + 50;
        // 5.读取 Redis 余额 user:balance:{userId}，未命中回退 DB
        String balanceKey = RedisKeyPrefix.USER_BALANCE_KEY + userId;
        String balanceStr = stringRedisTemplate.opsForValue().get(balanceKey);
        BigDecimal balance;
        if (!StringUtils.hasText(balanceStr)) {
            // 若redis未命中
            // 5.1.查询数据库
            UserWallet userWallet = lambdaQuery()
                    .eq(UserWallet::getUserId, userId)
                    .one();

            balance = userWallet.getBalance();
        } else {
            // 5.2.若redis命中
            balance = new BigDecimal(balanceStr);
        }
        // 6.更新 Redis：INCRBYFLOAT user:balance:{userId} reward
        // 注意：必须转为 double，否则 Java 会将 int 匹配到 increment(key, long) → INCRBY，
        // 而 Redis 中的余额由 Lua 脚本写为 "%.2f" 格式（如 "0.00"），INCRBY 只接受整数字符串会报错
        stringRedisTemplate.opsForValue().increment(balanceKey, (double) reward);
        // 7.写入 DB：
        // 7.1.UPDATE user_wallet SET balance = balance + reward, total_income = total_income + reward WHERE user_id=?
        BigDecimal rewardDecimal = BigDecimal.valueOf(reward);
        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate()
                .set(UserWallet::getBalance, balance.add(rewardDecimal))
                .setSql("total_income = total_income +" + rewardDecimal)
                .set(UserWallet::getUpdateTime, now)
                .eq(UserWallet::getUserId, userId)
                .update();
        // 7.2.INSERT INTO currency_transaction (user_id, amount, type, biz_type, balance_after) VALUES (?, reward, 'INCOME', 'DAILY_SIGNIN', newBalance)
        CurrencyTransaction currencyTransaction = CurrencyTransaction.builder()
                .id(snowflakeIdGenerator.nextId())
                .userId(userId)
                .amount(rewardDecimal)
                .type(CurrencyTransactionType.INCOME)
                .bizType(CurrencyBizType.DAILY_SIGNIN)
                .bizId(null)
                .balanceAfter(balance.add(rewardDecimal))
                .createTime(now)
                .build();
        currencyTransactionService.save(currencyTransaction);
        // 8.设置签到标记：SETBIT signin:{userId}:{year} {dayOfYear - 1} 1
        stringRedisTemplate.opsForValue().setBit(signinKey, dayOfYear - 1, true);
        // 9.计算连续签到天数，返回 SigninVO
        // 9.1.从今天往前数本年连续签到天数
        Integer count = countConsecutiveDays(signinKey, dayOfYear - 1);

        // 9.2.若从 1 月 1 日到今天全部签到，继续往前跨年查
        if (count == dayOfYear) {
            int prevYear = year - 1;
            while (true) {
                String prevYearKey = RedisKeyPrefix.SIGNIN_KEY + userId + ":" + prevYear;
                int daysInPrevYear = Year.of(prevYear).length();
                // 从上一年最后一天往前数连续天数，不存在则返回 0
                int prevConsecutive = countConsecutiveDays(prevYearKey, daysInPrevYear - 1);
                if (prevConsecutive == 0) {
                    break;
                }
                count += prevConsecutive;
                // 上一年全勤才能继续往前推，否则到此为止
                if (prevConsecutive == daysInPrevYear) {
                    prevYear--;
                } else {
                    break;
                }
            }
        }

        SigninVO signinVO = new SigninVO();
        signinVO.setBalance(balance.add(rewardDecimal));
        signinVO.setReward(reward);
        signinVO.setSignedDays(count);
        return Result.ok(signinVO);
    }

    /**
     * 从指定位位置往前数连续签到天数。
     * 直接用 Redis 底层连接获取原始字节，绕过 StringRedisTemplate 的 UTF-8 序列化（bitmap 是二进制数据，经过
     * String 编解码会丢失/损坏字节）；同时手动按 Redis big-endian 位序读 bit，不用 BitSet.valueOf()（little-endian）。
     *
     * @param key             bitmap 的 Redis key
     * @param fromBitPosition 从哪个 bit 位开始往前数（0-indexed，即 Redis SETBIT 的 offset）
     * @return 连续为 1 的 bit 个数；key 不存在时返回 0
     */
    private int countConsecutiveDays(String key, int fromBitPosition) {
        byte[] bytes = stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<byte[]>) connection ->
                        connection.get(key.getBytes())
        );
        if (bytes == null || bytes.length == 0) {
            return 0;
        }
        int count = 0;
        for (int i = fromBitPosition; i >= 0; i--) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);  // Redis bit 是高位在前（big-endian）
            if (byteIndex < bytes.length && ((bytes[byteIndex] >> bitIndex) & 1) == 1) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 内部接口-用户注销删除钱包
     *
     * @return
     */
    @Override
    public BigDecimal deleteUserWalletByUserId() {
        // 1.GET user:balance:{userId} 获取当前余额（未命中查 DB）
        Long userId = UserContextHolder.getUserId();
        String balanceKey = RedisKeyPrefix.USER_BALANCE_KEY + userId;
        String balanceStr = stringRedisTemplate.opsForValue().get(balanceKey);
        BigDecimal balance;
        if (!StringUtils.hasText(balanceStr)) {
            // 若redis未命中
            // 5.1.查询数据库
            UserWallet userWallet = lambdaQuery()
                    .eq(UserWallet::getUserId, userId)
                    .one();

            balance = userWallet.getBalance();
        } else {
            // 5.2.若redis命中
            balance = new BigDecimal(balanceStr);
        }
        // 2.余额快照记录到 currency_transaction（type=EXPENSE,biz_type=ACCOUNT_DELETION, amount=-balance）
        CurrencyTransaction currencyTransaction = CurrencyTransaction.builder()
                .id(snowflakeIdGenerator.nextId())
                .userId(userId)
                .amount(balance.negate())
                .type(CurrencyTransactionType.EXPENSE)
                .bizType(CurrencyBizType.ACCOUNT_DELETION)
                .bizId(null)
                .balanceAfter(BigDecimal.ZERO)
                .createTime(LocalDateTime.now())
                .build();
        currencyTransactionService.save(currencyTransaction);
        // 3.UPDATE user_wallet SET balance=0, status='CLOSED'
        LambdaQueryWrapper<UserWallet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserWallet::getUserId, userId);
        remove(wrapper);
        // 4.DEL user:balance:{userId}
        stringRedisTemplate.delete(balanceKey);
        // 5.返回
        return balance;
    }
}
