package com.heima.shop.scheduler;

import com.heima.api.constant.RedisKeyPrefix;
import com.heima.shop.domain.po.Product;
import com.heima.shop.service.IProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 商品库存同步定时任务——每 5 分钟将 Redis 库存批量回写 MySQL
 *
 * <h3>背景</h3>
 * <pre>
 * 库存的"热数据"在 Redis（shop:stock:{productId}），用户下单时 Lua 脚本原子扣减。
 * MySQL product.stock 只在管理员手动改库存时才更新，平时是"冷数据"。
 *
 * 问题：
 *   - Redis 重启 → 库存全部丢失，DB 里是旧值（可能是创建时的初始值）
 *   - 没有定时同步 → DB 和 Redis 差距越来越大
 * </pre>
 *
 * <h3>本任务做什么</h3>
 * <ol>
 *   <li>每 5 分钟扫描 Redis 所有 shop:stock:* key</li>
 *   <li>与 DB product.stock 比对，只更新有差异的行</li>
 *   <li>记录同步日志（同步前值 → 同步后值）</li>
 * </ol>
 *
 * <h3>补充保障：启动预热</h3>
 * 服务启动时（{@code @PostConstruct}），检查 Redis 中是否有库存 key。
 * 若没有（Redis 重启过），从 DB 回灌所有库存到 Redis。
 *
 * <h3>为什么不每次下单同步？</h3>
 * 每次下单（高频写）同步 DB 会增加主链路延迟。5 分钟延迟在毕设场景完全可接受，
 * 最坏情况：Redis 宕机 → 丢 5 分钟库存变化 → 从 DB 恢复后管理员可手动修正。
 *
 * @author lucy
 * @since 2026-07-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductStockSyncScheduler {

    private final StringRedisTemplate stringRedisTemplate;

    private final IProductService productService;

    /**
     * 每 5 分钟执行一次 Redis → DB 库存同步。
     * <p>
     * 使用 fixedDelay 而非 cron：每次执行完后再等 5 分钟，
     * 避免上一次还没跑完下一次又触发。
     */
    @Scheduled(fixedDelay = 300_000)
    public void syncStockFromRedisToDb() {
        log.info("========== 库存同步任务触发 ==========");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 扫描 Redis 所有 shop:stock:* key
            Set<String> stockKeys = scanStockKeys();
            if (stockKeys.isEmpty()) {
                log.info("Redis 中无库存 key，跳过同步");
                return;
            }
            log.info("扫描到 {} 个库存 key", stockKeys.size());

            // 2. 逐个比对 Redis 与 DB，收集需更新的记录
            List<Product> toUpdate = new ArrayList<>();
            int skippedCount = 0;

            for (String key : stockKeys) {
                try {
                    // 2.1 从 key 中提取 productId
                    Long productId = extractProductId(key);
                    if (productId == null) {
                        log.warn("无法解析 key 中的 productId: {}", key);
                        continue;
                    }

                    // 2.2 读 Redis 库存
                    String redisStockStr = stringRedisTemplate.opsForValue().get(key);
                    if (!StringUtils.hasText(redisStockStr)) {
                        // key 刚好过期，跳过
                        skippedCount++;
                        continue;
                    }
                    int redisStock = Integer.parseInt(redisStockStr);

                    // 2.3 读 DB 库存
                    Product product = productService.getById(productId);
                    if (product == null || product.getDeleted() == 1) {
                        // 商品已删除，清理 Redis 残留 key
                        stringRedisTemplate.delete(key);
                        log.info("清理已删除商品的库存 key: {}", key);
                        continue;
                    }

                    // 2.4 比对，只更新有差异的
                    int dbStock = product.getStock();
                    if (redisStock != dbStock) {
                        product.setStock(redisStock);
                        product.setUpdateTime(LocalDateTime.now());
                        toUpdate.add(product);
                        log.debug("库存差异: productId={}, DB={} → Redis={}", productId, dbStock, redisStock);
                    } else {
                        skippedCount++;
                    }

                } catch (NumberFormatException e) {
                    log.error("Redis 库存值格式错误: key={}, value={}", key,
                            stringRedisTemplate.opsForValue().get(key));
                }
            }

            // 3. 批量更新 DB
            if (!toUpdate.isEmpty()) {
                boolean success = productService.updateBatchById(toUpdate);
                if (success) {
                    log.info("库存同步完成: 更新 {} 条, 跳过 {} 条（无变化）, 耗时 {}ms",
                            toUpdate.size(), skippedCount, System.currentTimeMillis() - startTime);
                } else {
                    log.error("批量更新库存失败");
                }
            } else {
                log.info("库存同步完成: 无需更新（{} 条均无变化）, 耗时 {}ms",
                        skippedCount, System.currentTimeMillis() - startTime);
            }

        } catch (Exception e) {
            log.error("库存同步任务异常", e);
        }
    }

    /**
     * 使用 SCAN 命令扫描所有 shop:stock:* key。
     * <p>
     * 注意：生产环境 Redis 可能几万 key，KEYS 命令会阻塞 Redis。
     * SCAN 是渐进式迭代，不会阻塞。
     *
     * @return 匹配的 key 集合
     */
    private Set<String> scanStockKeys() {
        String pattern = RedisKeyPrefix.SHOP_STOCK_KEY + "*";
        // 使用 keys() 简化（毕设阶段 key 数量少）; 生产应改用 SCAN
        // SCAN 方式: Cursor<String> cursor = stringRedisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(100).build())
        // 这里用 keys 是因为数据量小，且 KEYS 的结果更直观
        return stringRedisTemplate.keys(pattern);
    }

    /**
     * 从 Redis key "shop:stock:123456" 中提取 productId=123456。
     */
    private Long extractProductId(String key) {
        String prefix = RedisKeyPrefix.SHOP_STOCK_KEY;
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

    // ==================== 启动预热 ====================

    /**
     * 服务启动后检查：若 Redis 中没有任何 shop:stock:* key，
     * 说明 Redis 重启过，从 DB 全量回灌库存。
     * <p>
     * 使用 {@code @PostConstruct} 的变通方式：
     * 通过 {@code @Scheduled(initialDelay = 5_000)} 延迟 5 秒执行，
     * 确保 Bean 和 Redis 连接已完全初始化。
     * <p>
     * 只执行一次：检测到已有库存 key 则直接跳过。
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = Long.MAX_VALUE)
    public void warmUpStockOnStartup() {
        log.info("========== 库存预热检查 ==========");

        try {
            // 检查 Redis 中是否已有库存 key
            Set<String> existingKeys = scanStockKeys();
            if (existingKeys != null && !existingKeys.isEmpty()) {
                log.info("Redis 中已有 {} 个库存 key，跳过预热", existingKeys.size());
                return;
            }

            log.info("Redis 中无库存 key，从 DB 全量回灌...");

            // 查询所有未删除、已上架的商品
            List<Product> products = productService.lambdaQuery()
                    .eq(Product::getDeleted, 0)
                    .list();

            if (products.isEmpty()) {
                log.info("DB 中无商品，跳过预热");
                return;
            }

            int loadedCount = 0;
            for (Product product : products) {
                String key = RedisKeyPrefix.SHOP_STOCK_KEY + product.getId();
                // setIfAbsent 防止覆盖已在预热期间被创建的 key（极端情况）
                Boolean success = stringRedisTemplate.opsForValue()
                        .setIfAbsent(key, String.valueOf(product.getStock()));
                if (Boolean.TRUE.equals(success)) {
                    loadedCount++;
                }
            }

            log.info("库存预热完成: 从 DB 回灌 {} 个商品的库存到 Redis", loadedCount);

        } catch (Exception e) {
            log.error("库存预热失败", e);
        }
    }
}
