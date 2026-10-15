package com.heima.analytics.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.analytics.domain.po.IntimacyEvent;
import com.heima.analytics.domain.po.WeeklyReport;
import com.heima.analytics.mapper.IntimacyEventMapper;
import com.heima.analytics.mapper.WeeklyReportMapper;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.enums.EventType;
import com.heima.common.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <h3>周报定时生成器 — 每周一 00:05 自动为所有宠物生成上一周的周报</h3>
 *
 * <h3>做什么</h3>
 * <ol>
 *   <li>每周一凌晨 00:05 触发</li>
 *   <li>计算上一周的 ISO 周号（如 "2026-W28"）和日期范围</li>
 *   <li>从 intimacy_event 表聚合上一周所有宠物的互动数据</li>
 *   <li>对每个有活动的宠物生成一条 weekly_report 记录</li>
 *   <li>已存在报告的宠物跳过（幂等）</li>
 * </ol>
 *
 * <h3>并发控制</h3>
 * 使用 Redisson 分布式锁 {@code report:lock:weekly}（TTL 300s），
 * 防止多实例同时执行导致重复生成报告。
 *
 * <h3>数据来源</h3>
 * 当前版本仅从 intimacy_event 表聚合：
 * <ul>
 *   <li>totalFeeds/Plays/Baths — 按 event_type 分组计数</li>
 *   <li>intimacyGain — SUM(score_change)</li>
 *   <li>avgMood — 当前置为 null（后续版本可从 pet-service 拉取历史心情数据）</li>
 * </ul>
 *
 * <h3>手动触发兜底</h3>
 * 若定时任务因服务器维护错过执行，用户可通过
 * {@code POST /api/analytics/weekly-report/{petId}} 手动生成单只宠物的报告。
 *
 * @author lucy
 * @since 2026-07-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final IntimacyEventMapper intimacyEventMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_KEY = "report:lock:weekly";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 每周一凌晨 00:05 执行。
     *
     * <p>选 00:05 而不是 00:00：留 5 分钟缓冲，确保跨天逻辑完成、
     * 前一天的 Stream 消息已被消费完毕。</p>
     */
    @Scheduled(cron = "0 5 0 * * MON")
    public void generateWeeklyReports() {
        log.info("========== 周报定时生成任务触发 ==========");

        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            // 尝试获取分布式锁（不等，拿不到说明另一个实例已在执行）
            if (!lock.tryLock(0, 300, TimeUnit.SECONDS)) {
                log.info("周报生成任务已在其他实例运行，跳过");
                return;
            }

            // 1. 确定报告周期（上一周）
            LocalDate today = LocalDate.now();
            // 今天是周一，上一周 = 上周一 ~ 上周日
            LocalDate weekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            String reportWeek = formatIsoWeek(weekStart);

            log.info("生成周报: reportWeek={}, 日期范围 {} ~ {}", reportWeek, weekStart, weekEnd);

            // 2. 查询上一周所有亲密度事件
            LocalDateTime startTime = weekStart.atStartOfDay();
            LocalDateTime endTime = weekEnd.plusDays(1).atStartOfDay(); // 不含右边界

            List<IntimacyEvent> events = intimacyEventMapper.selectList(
                    new LambdaQueryWrapper<IntimacyEvent>()
                            .ge(IntimacyEvent::getCreateTime, startTime)
                            .lt(IntimacyEvent::getCreateTime, endTime));

            if (events.isEmpty()) {
                log.info("上一周无亲密度事件，跳过周报生成");
                return;
            }

            log.info("查询到 {} 条亲密度事件", events.size());

            // 3. 按 (userId, petId) 分组聚合
            Map<String, List<IntimacyEvent>> grouped = events.stream()
                    .collect(Collectors.groupingBy(e -> e.getUserId() + ":" + e.getPetId()));

            int generated = 0;
            int skipped = 0;

            for (Map.Entry<String, List<IntimacyEvent>> entry : grouped.entrySet()) {
                List<IntimacyEvent> petEvents = entry.getValue();
                Long userId = petEvents.get(0).getUserId();
                Long petId = petEvents.get(0).getPetId();

                try {
                    if (tryGenerateReport(userId, petId, reportWeek, weekStart, weekEnd, petEvents)) {
                        generated++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.error("生成周报失败: userId={}, petId={}", userId, petId, e);
                }
            }

            log.info("周报生成完成: 生成 {} 份, 跳过 {} 份（已存在）", generated, skipped);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("周报生成被中断");
        } catch (Exception e) {
            log.error("周报生成任务异常", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 为单个宠物生成一份周报。
     *
     * @return true=生成成功, false=已存在则跳过
     */
    private boolean tryGenerateReport(Long userId, Long petId, String reportWeek,
                                      LocalDate weekStart, LocalDate weekEnd,
                                      List<IntimacyEvent> events) {
        // 幂等检查：本周是否已生成报告
        long existCount = weeklyReportMapper.selectCount(
                new LambdaQueryWrapper<WeeklyReport>()
                        .eq(WeeklyReport::getPetId, petId)
                        .eq(WeeklyReport::getReportWeek, reportWeek));
        if (existCount > 0) {
            log.debug("周报已存在: petId={}, reportWeek={}", petId, reportWeek);
            return false;
        }

        // 聚合统计
        long totalFeeds = events.stream().filter(e -> EventType.FEED == e.getEventType()).count();
        long totalPlays = events.stream().filter(e -> EventType.PLAY == e.getEventType()).count();
        long totalBaths = events.stream().filter(e -> EventType.BATHE == e.getEventType()).count();
        int intimacyGain = events.stream().mapToInt(IntimacyEvent::getScoreChange).sum();

        // 平均心情：从每日快照取本周 7 天 mood 求均值，无快照则为 null
        BigDecimal avgMood = calculateAvgMood(petId, weekStart, weekEnd);

        // 生成摘要
        String weekRange = weekStart.format(DATE_FMT) + " ~ " + weekEnd.format(DATE_FMT);
        String summary = buildSummary(totalFeeds, totalPlays, totalBaths, intimacyGain, weekRange);

        // 构建 reportJson
        String reportJson = buildReportJson(reportWeek, weekRange,
                totalFeeds, totalPlays, totalBaths, intimacyGain, summary);

        // 入库
        WeeklyReport report = new WeeklyReport()
                .setId(snowflakeIdGenerator.nextId())
                .setUserId(userId)
                .setPetId(petId)
                .setReportWeek(reportWeek)
                .setTotalFeeds((int) totalFeeds)
                .setTotalPlays((int) totalPlays)
                .setTotalBaths((int) totalBaths)
                .setAvgMood(avgMood)
                .setIntimacyGain(intimacyGain)
                .setReportJson(reportJson)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());

        weeklyReportMapper.insert(report);
        log.debug("周报已生成: petId={}, reportWeek={}, 喂食{} 玩耍{} 洗澡{} 亲密度+{}",
                petId, reportWeek, totalFeeds, totalPlays, totalBaths, intimacyGain);
        return true;
    }

    /**
     * 从每日心情快照计算本周平均心情。
     *
     * <p>快照 key 格式: {@code pet:mood:daily:{petId}:{yyyyMMdd}}
     * 由 pet-service 的 PetMoodSnapshotScheduler 每晚 23:55 写入，
     * TTL 60 天自动过期。</p>
     *
     * @param petId    宠物 ID
     * @param weekStart 本周一
     * @param weekEnd   本周日
     * @return 7 天心情快照的平均值（保留一位小数）；若没有任何快照则返回 null
     */
    private BigDecimal calculateAvgMood(Long petId, LocalDate weekStart, LocalDate weekEnd) {
        int sum = 0;
        int count = 0;

        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            String key = RedisKeyPrefix.PET_MOOD_DAILY_KEY + petId + ":" + date.format(DATE_FMT);
            try {
                String moodStr = stringRedisTemplate.opsForValue().get(key);
                if (moodStr != null && !moodStr.isEmpty()) {
                    sum += Integer.parseInt(moodStr);
                    count++;
                }
            } catch (NumberFormatException e) {
                log.debug("心情快照值格式异常: key={}, value={}", key,
                        stringRedisTemplate.opsForValue().get(key));
            }
        }

        if (count == 0) {
            return null;  // 本周无任何快照（定时任务刚上线或宠物本周刚领养）
        }

        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    /**
     * 生成中文摘要文本。
     */
    private String buildSummary(long feeds, long plays, long baths, int intimacyGain,
                                String weekRange) {
        StringBuilder sb = new StringBuilder();
        sb.append("本周（").append(weekRange).append("）宠物表现");

        int totalInteract = (int) (feeds + plays + baths);
        if (totalInteract == 0) {
            sb.append("平静，暂无互动记录。");
        } else {
            sb.append("不错！");
            if (feeds > 0) sb.append("共喂食 ").append(feeds).append(" 次、");
            if (plays > 0) sb.append("玩耍 ").append(plays).append(" 次、");
            if (baths > 0) sb.append("洗澡 ").append(baths).append(" 次。");
            // 去掉末尾的顿号换句号
            if (sb.charAt(sb.length() - 1) == '、') {
                sb.setCharAt(sb.length() - 1, '。');
            }
        }

        if (intimacyGain > 0) {
            sb.append("亲密度增长 ").append(intimacyGain).append("，");
            sb.append("继续保持哦~");
        } else if (intimacyGain < 0) {
            sb.append("亲密度下降 ").append(Math.abs(intimacyGain)).append("，");
            sb.append("要多陪陪它呀！");
        } else {
            sb.append("亲密度没有变化，多互动可以让宠物更亲近你哦~");
        }

        return sb.toString();
    }

    /**
     * 构建 reportJson（不含敏感信息，用于前端展示或调试）。
     */
    private String buildReportJson(String reportWeek, String weekRange,
                                   long totalFeeds, long totalPlays, long totalBaths,
                                   int intimacyGain, String summary) {
        return String.format(
                "{\"reportWeek\":\"%s\",\"weekRange\":\"%s\"," +
                        "\"totalFeeds\":%d,\"totalPlays\":%d,\"totalBaths\":%d," +
                        "\"intimacyGain\":%d,\"summary\":\"%s\"}",
                reportWeek, weekRange,
                totalFeeds, totalPlays, totalBaths,
                intimacyGain,
                escapeJson(summary));
    }

    /**
     * ISO 周号格式化，如 "2026-W28"。
     */
    private String formatIsoWeek(LocalDate monday) {
        int year = monday.get(IsoFields.WEEK_BASED_YEAR);
        int week = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    /**
     * 简单 JSON 字符串转义。
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
