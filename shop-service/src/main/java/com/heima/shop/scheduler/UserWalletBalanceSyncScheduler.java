package com.heima.shop.scheduler;

import com.heima.api.constant.RedisKeyPrefix;
import com.heima.shop.domain.po.UserWallet;
import com.heima.shop.service.IUserWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserWalletBalanceSyncScheduler {

    private final StringRedisTemplate stringRedisTemplate;

    private final IUserWalletService userWalletService;

    /**
     * 每5分钟执行一次-》实现redis数据同步到数据库
     */
    @Scheduled(fixedDelay = 300_000)
    public void syncBalanceFromRedisToDb() {
        log.info("=====余额同步任务触发=====");
        long startTime = System.currentTimeMillis();

        try {
            // 检查redis中是否有余额数据
            Set<String> existingKeys = scanBalanceKey();
            if (existingKeys.isEmpty()) {
                log.info("Redis 中无库存 key，跳过同步");
                return;
            }
            log.info("扫描到 {} 个库存 key", existingKeys.size());

            // 2.逐个比对 Redis 与 DB，收集需更新的记录
            List<UserWallet> toUpdate = new ArrayList<>();
            int skippedCount = 0;

            for (String key : existingKeys) {
                try {
                    // 2.1.从key中提取userId
                    Long userId = extractUserID(key);

                    if (userId == null) {
                        log.info("无法解析key中的userId,key={}", key);
                        continue;
                    }
                    // 2.2.读取redis中的库存
                    String balanceStr = stringRedisTemplate.opsForValue().get(key);
                    if (!StringUtils.hasText(balanceStr)) {
                        skippedCount++;
                        continue;
                    }
                    BigDecimal redisBalance = new BigDecimal(balanceStr);
                    // 2.3.读取DB余额
                    UserWallet userWallet = userWalletService.lambdaQuery()
                            .eq(UserWallet::getUserId, userId)
                            .one();
                    if (userWallet == null) {
                        // 用户已经注销，用户钱包已经删除，清理redis残余的key
                        stringRedisTemplate.delete(key);
                        log.info("清理已删除商品的库存 key: {}", key);
                        continue;
                    }
                    // 2.4 比对，只更新有差异的
                    BigDecimal dbBalance = userWallet.getBalance();
                    if (!redisBalance.equals(dbBalance)) {
                        userWallet.setBalance(redisBalance);
                        userWallet.setUpdateTime(LocalDateTime.now());
                        toUpdate.add(userWallet);
                        log.debug("余额差异：userWalletId={}，DB={}，Redis={}", userWallet.getId(), dbBalance, redisBalance);
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.error("Redis 库存值格式错误: key={}, value={}", key,
                            stringRedisTemplate.opsForValue().get(key));
                }
            }

            // 3. 批量更新 DB
            if (!toUpdate.isEmpty()){
                boolean success = userWalletService.updateBatchById(toUpdate);
                if (success) {
                    log.info("库存同步完成: 更新 {} 条, 跳过 {} 条（无变化）, 耗时 {}ms",
                            toUpdate.size(), skippedCount, System.currentTimeMillis() - startTime);
                } else {
                    log.error("批量更新库存失败");
                }
            }else {
                log.info("库存同步完成: 无需更新（{} 条均无变化）, 耗时 {}ms",
                        skippedCount, System.currentTimeMillis() - startTime);
            }

        } catch (Exception e) {
            log.error("余额同步任务时报");
        }
    }

    // 从key中提取userId
    private Long extractUserID(String key) {
        String prefix = RedisKeyPrefix.USER_BALANCE_KEY;
        if (!key.startsWith(prefix)) {
            return null;
        }
        String idStr = key.substring(prefix.length());
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 服务启动后检查：若 Redis 中没有任何
     * 说明 Redis 重启过，从 DB 全量回灌库存。
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = Long.MAX_VALUE)
    public void warmUpBalanceOnStartup() {
        log.info("=====余额预热检查=====");

        try {
            // 检查redis中是否有余额数据
            Set<String> existingKeys = scanBalanceKey();
            if (existingKeys != null || !existingKeys.isEmpty()) {
                log.info("redis中已有{}个库存，直接跳过", existingKeys.size());
                return;
            }

            log.info("Redis 中无库存 key，从 DB 全量回灌...");

            //查询所以用户钱包
            List<UserWallet> list = userWalletService.list();
            if (list == null) {
                log.info("无用户注册，直接跳过");
                return;
            }

            int loadCount = 0;
            for (UserWallet userWallet : list) {
                String balanceKey = RedisKeyPrefix.USER_BALANCE_KEY + userWallet.getUserId();
                Boolean success = stringRedisTemplate.opsForValue()
                        .setIfAbsent(balanceKey, String.valueOf(userWallet.getBalance()));
                if (Boolean.TRUE.equals(success)) {
                    loadCount++;
                }
            }

            log.info("库存预热完成: 从 DB 回灌 {} 个商品的库存到 Redis", loadCount);
        } catch (Exception e) {
            log.error("=====余额预热失败=====");
        }
    }

    private Set<String> scanBalanceKey() {
        String pattern = RedisKeyPrefix.USER_BALANCE_KEY + "*";
        return stringRedisTemplate.keys(pattern);
    }
}
