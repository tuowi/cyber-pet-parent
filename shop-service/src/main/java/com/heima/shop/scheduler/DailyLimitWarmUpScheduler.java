package com.heima.shop.scheduler;

import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.enums.OrderStatus;
import com.heima.shop.domain.po.Product;
import com.heima.shop.domain.po.ShopOrder;
import com.heima.shop.service.IProductService;
import com.heima.shop.service.IShopOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 每日限购计数器预热——Redis 重启后从订单表重建 shop:limit:* 计数器。
 *
 * <h3>背景</h3>
 * <pre>
 * shop:limit:{userId}:{productId}:{yyyy-MM-dd} 是当日临时计数器，TTL 到当天 24:00。
 * 与 stock / balance 不同，这个 key 没有对应的 DB 字段可以周期性同步。
 *
 * 风险：
 *   - Redis 重启 → 所有 limit key 丢失
 *   - Lua 脚本 deduct_stock.lua 中 GET 不到 key 时返回 0，限购被绕过
 *   - ProductServiceImpl 的懒加载重建只覆盖"浏览了详情页"的用户+商品
 *
 * 解决方案：
 *   启动时从 shop_order 表聚合今天所有已支付订单，批量重建 limit 计数器。
 *   只针对 dailyLimit != null 的商品（不限购的商品不需要 limit key）。
 * </pre>
 *
 * <h3>为什么不周期性同步？</h3>
 * limit key 是日期限定的临时数据，每天 0 点自动过期。没有永久 DB 表存放"今日已买几件"，
 * 也不需要——订单表 shop_order 本身就是真实的购买记录。
 *
 * @author lucy
 * @since 2026-07-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLimitWarmUpScheduler {

    private final StringRedisTemplate stringRedisTemplate;

    private final IShopOrderService shopOrderService;

    private final IProductService productService;

    /**
     * 启动后延迟 8 秒执行一次，确保 DB 连接和 Bean 均已就绪。
     * fixedDelay = Long.MAX_VALUE 保证只执行一次。
     */
    @Scheduled(initialDelay = 8_000, fixedDelay = Long.MAX_VALUE)
    public void warmUpLimitCountersOnStartup() {
        log.info("========== 限购计数器预热检查 ==========");

        try {
            // 1. 快速检查：Redis 中是否已有 shop:limit:* key
            Set<String> existingKeys = stringRedisTemplate.keys(RedisKeyPrefix.SHOP_LIMIT_KEY + "*");
            if (existingKeys != null && !existingKeys.isEmpty()) {
                log.info("Redis 中已有 {} 个限购计数器 key，跳过预热", existingKeys.size());
                return;
            }

            log.info("Redis 中无限购计数器 key，从 DB 重建...");

            // 2. 查询所有有限购的商品（dailyLimit != null 且未删除）
            List<Product> limitedProducts = productService.lambdaQuery()
                    .eq(Product::getDeleted, 0)
                    .isNotNull(Product::getDailyLimit)
                    .list();

            if (limitedProducts.isEmpty()) {
                log.info("没有配置限购的商品，跳过预热");
                return;
            }

            Set<Long> limitedProductIds = limitedProducts.stream()
                    .map(Product::getId)
                    .collect(Collectors.toSet());

            log.info("配置了限购的商品共 {} 个: {}", limitedProductIds.size(), limitedProductIds);

            // 3. 查询今天所有已支付订单
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            LocalDateTime startOfTomorrow = LocalDate.now().plusDays(1).atStartOfDay();

            List<ShopOrder> todayOrders = shopOrderService.lambdaQuery()
                    .eq(ShopOrder::getStatus, OrderStatus.PAID)
                    .ge(ShopOrder::getCreateTime, startOfToday)
                    .lt(ShopOrder::getCreateTime, startOfTomorrow)
                    .list();

            if (todayOrders.isEmpty()) {
                log.info("今天还没有订单，无需重建限购计数器");
                return;
            }

            // 4. 按 (userId, productId) 聚合购买数量，只保留有限购的商品
            Map<String, Integer> aggregated = todayOrders.stream()
                    .filter(order -> limitedProductIds.contains(order.getProductId()))
                    .collect(Collectors.groupingBy(
                            order -> order.getUserId() + ":" + order.getProductId(),
                            Collectors.summingInt(ShopOrder::getQuantity)
                    ));

            if (aggregated.isEmpty()) {
                log.info("今天的订单均不涉及限购商品，跳过");
                return;
            }

            // 5. 批量写入 Redis，TTL 到当天结束
            long secondsUntilMidnight = ChronoUnit.SECONDS.between(LocalDateTime.now(), startOfTomorrow);
            int writtenCount = 0;

            for (Map.Entry<String, Integer> entry : aggregated.entrySet()) {
                String[] parts = entry.getKey().split(":");
                String userId = parts[0];
                String productId = parts[1];
                int bought = entry.getValue();

                String limitKey = RedisKeyPrefix.SHOP_LIMIT_KEY + userId + ":" + productId + ":" + today;
                stringRedisTemplate.opsForValue()
                        .set(limitKey, String.valueOf(bought), secondsUntilMidnight, TimeUnit.SECONDS);
                writtenCount++;

                log.debug("重建限购计数器: key={}, bought={}, ttl={}s", limitKey, bought, secondsUntilMidnight);
            }

            log.info("限购计数器预热完成: 重建 {} 个计数器, TTL={}s（到今日 24:00）",
                    writtenCount, secondsUntilMidnight);

        } catch (Exception e) {
            log.error("限购计数器预热失败", e);
        }
    }
}
