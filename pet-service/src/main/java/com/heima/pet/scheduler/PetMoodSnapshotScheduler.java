package com.heima.pet.scheduler;

import com.heima.api.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <h3>每日心情快照 — 每晚 23:55 将 Redis 中所有宠物的当前 mood 值快照保存</h3>
 *
 * <h3>为什么需要这个</h3>
 * Redis {@code pet:state:{petId}} 只存当前值，每次衰减直接覆盖旧 mood。
 * 没有历史心情数据，周报的 avgMood 就无法计算。
 * 本任务每晚取一次快照，写入 {@code pet:mood:daily:{petId}:{yyyyMMdd}}，
 * 60 天 TTL 自动过期，周报生成时取 7 天快照算均值。
 *
 * <h3>Key 设计</h3>
 * <pre>
 * pet:mood:daily:{petId}:20260728  → "75"
 * TTL: 60 天
 * </pre>
 *
 * @author lucy
 * @since 2026-07-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetMoodSnapshotScheduler {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 每晚 23:55 执行。
     *
     * <p>选 23:55 而不是 00:00：接近一天结束，mood 值最能反映当日真实状态。
     * 5 分钟缓冲防止与日期翻转的边界问题。</p>
     */
//    @Scheduled(cron = "0 55 23 * * ?")
    @Scheduled(cron = "0 */5 * * * ?")
    public void snapshotMood() {
        log.info("========== 每日心情快照任务触发 ==========");
        String today = LocalDate.now().format(DATE_FMT);

        try {
            Set<String> keys = stringRedisTemplate.keys(RedisKeyPrefix.PET_STATE_KEY + "*");
            if (keys == null || keys.isEmpty()) {
                log.info("无宠物状态缓存，跳过快照");
                return;
            }

            int snapshotCount = 0;
            for (String stateKey : keys) {
                try {
                    if (doSnapshot(stateKey, today)) {
                        snapshotCount++;
                    }
                } catch (Exception e) {
                    log.error("心情快照失败: key={}", stateKey, e);
                }
            }

            log.info("心情快照完成: {} 个宠物已保存 (date={})", snapshotCount, today);
        } catch (Exception e) {
            log.error("心情快照任务异常", e);
        }
    }

    /**
     * 对单个宠物执行快照。
     *
     * @return true 表示成功快照，false 表示跳过（无 mood 字段）
     */
    private boolean doSnapshot(String stateKey, String today) {
        // 提取 petId
        String petIdStr = stateKey.substring(RedisKeyPrefix.PET_STATE_KEY.length());
        if (petIdStr.isEmpty()) {
            return false;
        }

        // 从 Hash 中只读 mood 字段（HGET，比 HGETALL 高效）
        Object moodObj = stringRedisTemplate.opsForHash().get(stateKey, "mood");
        if (moodObj == null) {
            return false;
        }

        String moodStr = moodObj.toString();
        String snapshotKey = RedisKeyPrefix.PET_MOOD_DAILY_KEY + petIdStr + ":" + today;

        // SETEX 原子写值 + 设过期
        stringRedisTemplate.opsForValue().set(
                snapshotKey, moodStr,
                RedisKeyPrefix.PET_MOOD_DAILY_TTL, TimeUnit.DAYS);

        return true;
    }
}
