package com.heima.analytics.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.analytics.domain.po.IntimacyEvent;
import com.heima.analytics.domain.po.WeeklyReport;
import com.heima.analytics.domain.po.json.WeeklyReportJson;
import com.heima.analytics.domain.query.PageQuery;
import com.heima.analytics.domain.vo.WeeklyReportStatus;
import com.heima.analytics.domain.vo.WeeklyReportVO;
import com.heima.analytics.mapper.WeeklyReportMapper;
import com.heima.analytics.service.IIntimacyEventService;
import com.heima.analytics.service.IWeeklyReportService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.api.client.PetClient;
import com.heima.api.client.ShopClient;
import com.heima.api.client.SocialClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.EventType;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 宠物周报表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl extends ServiceImpl<WeeklyReportMapper, WeeklyReport> implements IWeeklyReportService {

    private final PetClient petClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StringRedisTemplate stringRedisTemplate;

    private final ShopClient shopClient;

    private final IIntimacyEventService intimacyEventService;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 获取最新周报
     *
     * @param petId
     * @return
     */
    @Override
    public Result<WeeklyReportVO> getNewWeeklyReport(Long petId) {
        // 1.校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.查询 weekly_report：WHERE pet_id=? ORDER BY create_time DESC LIMIT 1
        WeeklyReport weeklyReport = lambdaQuery()
                .eq(WeeklyReport::getPetId, petId)
                .orderByDesc(WeeklyReport::getCreateTime)
                .last("LIMIT 1")
                .one();
        if (weeklyReport == null) {
            return Result.ok(null);
        }

        // 查询宠物信息
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该宠物不存在");
        }
        // 3.若找到且 report_week = 当前 ISO 周：直接返回该报告
        // 方式一：直接格式化为 "年-周"（推荐，防跨年）
        //String currentWeek = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYY-ww"));
        // 例如 2026-07-30 输出 -> "2026-31"

        // 方式二：分别获取年份和周数（更灵活）
        LocalDate now = LocalDate.now();
        int year = now.get(WeekFields.ISO.weekBasedYear());    // 基于 ISO 的年份（跨年时与日历年不同）
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear()); // ISO 周数
        String currentWeekKey = year + "-W" + String.format("%02d", week); // "2026-W31"

        // 本周
        if (weeklyReport.getReportWeek().equals(currentWeekKey)) {
            // 得到时间段范围
            String weekRange = getWeekToTodayRange();

            WeeklyReportJson weeklyReportJson = new WeeklyReportJson();
            // 转换json数据
            try {
                weeklyReportJson = objectMapper.readValue(weeklyReport.getReportJson(), WeeklyReportJson.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            // 类型转换，作为返回体一部分
            WeeklyReportStatus status = BeanUtil.copyProperties(weeklyReportJson, WeeklyReportStatus.class);
            status.setAvgMood(weeklyReport.getAvgMood());

            // 当前亲密度总值
            // 直接从 Redis ZSCORE intimacy:leaderboard {userId}:{petId} 获取，不需要存表，返回时实时查即可
            Double score = stringRedisTemplate.opsForZSet()
                    .score(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, userId + ":" + petId);
            Integer currentIntimacy = score != null ? score.intValue() : 0;
            status.setCurrentIntimacy(currentIntimacy);

            // 本周金币收入
            // 查 currency_transaction 表，type='INCOME' 且 create_time 在本周范围内的 SUM(amount)
            LocalDate today = LocalDate.now();
            // 本周周一（ISO 标准，周一为第一天）
            LocalDate monday = today.with(WeekFields.ISO.dayOfWeek(), 1);
            // start = 周一 00:00:00
            LocalDateTime start = monday.atStartOfDay();
            // end = 今天 23:59:59 的下一秒，即明天 00:00:00
            LocalDateTime end = today.plusDays(1).atStartOfDay();
            BigDecimal totalEarnings = shopClient.getWeekTodayTotalEarnings(userId, start, end);

            // 本周金币支出
            BigDecimal totalExpenses = shopClient.getWeekTodayTotalExpenses(userId, start, end);

            status.setTotalEarnings(totalEarnings);
            status.setTotalExpenses(totalExpenses);

            WeeklyReportVO build = WeeklyReportVO.builder()
                    .reportId(weeklyReport.getId())
                    .petId(petId)
                    .petName(petInfo.getPetName())
                    .reportWeek(weeklyReport.getReportWeek())
                    .weekRange(weekRange)
                    .status(status)
                    .summary(weeklyReportJson.getSummary())
                    .createTime(weeklyReport.getCreateTime())
                    .updateTime(weeklyReport.getUpdateTime())
                    .build();

            return Result.ok(build);
        }

        // 4.若找到但 report_week 不是当前周：返回该"最新"报告（上一周的，仍然有效）
        String lastWeekKey = year + "-W" + String.format("%02d", week - 1);
        if (weeklyReport.getReportWeek().equals(lastWeekKey)) {
            WeeklyReportJson weeklyReportJson = new WeeklyReportJson();
            // 转换json数据
            try {
                weeklyReportJson = objectMapper.readValue(weeklyReport.getReportJson(), WeeklyReportJson.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            // 类型转换，作为返回体一部分
            WeeklyReportStatus status = BeanUtil.copyProperties(weeklyReportJson, WeeklyReportStatus.class);
            status.setAvgMood(weeklyReport.getAvgMood());

            // 当前亲密度总值
            // 直接从 Redis ZSCORE intimacy:leaderboard {userId}:{petId} 获取，不需要存表，返回时实时查即可
            Double score = stringRedisTemplate.opsForZSet()
                    .score(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, userId + ":" + petId);
            Integer currentIntimacy = score != null ? score.intValue() : 0;
            status.setCurrentIntimacy(currentIntimacy);

            // 本周金币收入
            // 查 currency_transaction 表，type='INCOME' 且 create_time 在本周范围内的 SUM(amount)
            LocalDate today = LocalDate.now();
            // 当前日期减去一周，得到上周的某一天
            LocalDate lastWeekDate = today.minusWeeks(1);
            // 计算该天所在周的周一（ISO标准，周一为一周第一天）
            LocalDate lastMonday = lastWeekDate.with(WeekFields.ISO.dayOfWeek(), 1);
            LocalDate lastSunday = lastMonday.plusDays(6);
            // start = 周一 00:00:00
            LocalDateTime start = lastMonday.atStartOfDay();
            // end = 今天 23:59:59 的下一秒，即明天 00:00:00
            LocalDateTime end = lastSunday.plusDays(1).atStartOfDay();
            BigDecimal totalEarnings = shopClient.getWeekTodayTotalEarnings(userId, start, end);

            // 本周金币支出
            BigDecimal totalExpenses = shopClient.getWeekTodayTotalExpenses(userId, start, end);

            status.setTotalEarnings(totalEarnings);
            status.setTotalExpenses(totalExpenses);

            WeeklyReportVO build = WeeklyReportVO.builder()
                    .reportId(weeklyReport.getId())
                    .petId(petId)
                    .petName(petInfo.getPetName())
                    .reportWeek(weeklyReport.getReportWeek())
                    .weekRange(getLastWeekRange())
                    .status(status)
                    .summary(weeklyReportJson.getSummary())
                    .createTime(weeklyReport.getCreateTime())
                    .updateTime(weeklyReport.getUpdateTime())
                    .build();

            return Result.ok(build);
        }
        // 5.若未找到任何报告：返回 data=null
        return Result.ok(null);
    }

    // 得到上周时间段范围
    private String getLastWeekRange() {
        LocalDate today = LocalDate.now();
        // 当前日期减去一周，得到上周的某一天
        LocalDate lastWeekDate = today.minusWeeks(1);
        // 计算该天所在周的周一（ISO标准，周一为一周第一天）
        LocalDate lastMonday = lastWeekDate.with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate lastSunday = lastMonday.plusDays(6);
        return lastMonday + " ~ " + lastSunday;
    }

    // 得到本周时间段范围
    private String getWeekToTodayRange() {
        LocalDate today = LocalDate.now();
        // 本周周一（ISO 标准，周一为第一天）
        LocalDate monday = today.with(WeekFields.ISO.dayOfWeek(), 1);

        // 如果 today 就是周一，则范围是 "周一 ~ 周一"
        return monday + " ~ " + today;
    }

    /**
     * 手动生成 / 更新本周周报。
     *
     * <h3>与原来设计的区别</h3>
     * 原来：已存在则返回 400 "请勿重复操作"。
     * 现在：<b>upsert 模式</b>——已存在则更新，不存在则新建。
     * 这样用户周三生成周报后，周六可以再点一次刷新数据。
     *
     * <h3>聚合数据来源</h3>
     * <ul>
     *   <li>totalFeeds / totalPlays / totalBaths — intimacy_event 表按 event_type 计数</li>
     *   <li>intimacyGain — SUM(score_change)</li>
     *   <li>avgMood — 从每日心情快照 pet:mood:daily:{petId}:{yyyyMMdd} 取均值</li>
     *   <li>totalEarnings / totalExpenses / currentIntimacy — 不在生成时存储，
     *       在 {@link #getNewWeeklyReport(Long)} 中实时查询</li>
     * </ul>
     *
     * @param petId 宠物 ID
     * @return 周报 ID
     */
    @Override
    public Result<Long> editWeeklyReport(Long petId) {
        // 1. 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }

        // 2. 校验宠物存在且属于当前用户
        PetInfo petInfo;
        try {
            petInfo = petClient.getBasicPetInfo(petId);
        } catch (Exception e) {
            log.error("Feign 调用 petClient.getBasicPetInfo 失败: petId={}", petId, e);
            return Result.fail(500, "查询宠物信息失败");
        }
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该宠物不存在");
        }
        if (!petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "该宠物不属于您");
        }

        // 3. 计算当前 ISO 周（如 "2026-W31"），与 DB 中 report_week 格式一致
        LocalDate today = LocalDate.now();
        int year = today.get(WeekFields.ISO.weekBasedYear());
        int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        String currentWeek = year + "-W" + String.format("%02d", week);

        // 4. 计算时间范围（本周一 ~ 今天）
        LocalDate monday = today.with(WeekFields.ISO.dayOfWeek(), 1);
        String weekRange = monday + " ~ " + today;
        // 周一至少要与今天相同或更早（防御：如果 today 就是周一，范围是 "周一 ~ 周一"）
        if (monday.isAfter(today)) {
            monday = today;
        }

        // 5. 查询本周 intimacy_event 记录
        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay(); // 不含右边界
        List<IntimacyEvent> eventList = intimacyEventService.lambdaQuery()
                .ge(IntimacyEvent::getCreateTime, start)
                .lt(IntimacyEvent::getCreateTime, end)
                .eq(IntimacyEvent::getPetId, petId)
                .eq(IntimacyEvent::getUserId, userId)
                .list();

        // 6. 聚合统计
        long totalFeeds = eventList.stream().filter(e -> EventType.FEED == e.getEventType()).count();
        long totalPlays = eventList.stream().filter(e -> EventType.PLAY == e.getEventType()).count();
        long totalBaths = eventList.stream().filter(e -> EventType.BATHE == e.getEventType()).count();
        int intimacyGain = eventList.stream().mapToInt(IntimacyEvent::getScoreChange).sum();

        // 7. 从每日快照计算平均心情
        BigDecimal avgMood = calculateAvgMood(petId, monday, today);

        // 8. 生成摘要和周报 JSON
        String summary = buildSummary(totalFeeds, totalPlays, totalBaths, intimacyGain, weekRange);
        String reportJson = buildReportJson(currentWeek, weekRange,
                (int) totalFeeds, (int) totalPlays, (int) totalBaths, intimacyGain, summary);

        // 9. Upsert：已存在则更新，不存在则新建
        WeeklyReport existing = lambdaQuery()
                .eq(WeeklyReport::getPetId, petId)
                .eq(WeeklyReport::getReportWeek, currentWeek)
                .one();

        if (existing != null) {
            // 更新已有报告
            existing.setTotalFeeds((int) totalFeeds);
            existing.setTotalPlays((int) totalPlays);
            existing.setTotalBaths((int) totalBaths);
            existing.setAvgMood(avgMood);
            existing.setIntimacyGain(intimacyGain);
            existing.setReportJson(reportJson);
            existing.setUpdateTime(LocalDateTime.now());
            updateById(existing);
            log.info("周报已更新: reportId={}, petId={}, reportWeek={}", existing.getId(), petId, currentWeek);
            return Result.ok(existing.getId());
        } else {
            // 新建报告
            WeeklyReport report = new WeeklyReport()
                    .setId(snowflakeIdGenerator.nextId())
                    .setUserId(userId)
                    .setPetId(petId)
                    .setReportWeek(currentWeek)
                    .setTotalFeeds((int) totalFeeds)
                    .setTotalPlays((int) totalPlays)
                    .setTotalBaths((int) totalBaths)
                    .setAvgMood(avgMood)
                    .setIntimacyGain(intimacyGain)
                    .setReportJson(reportJson)
                    .setCreateTime(LocalDateTime.now())
                    .setUpdateTime(LocalDateTime.now());
            save(report);
            log.info("周报已生成: reportId={}, petId={}, reportWeek={}", report.getId(), petId, currentWeek);
            return Result.ok(report.getId());
        }
    }

    // ==================== 周报生成辅助方法 ====================

    /**
     * 从每日心情快照计算本周平均心情。
     *
     * <p>快照 key: {@code pet:mood:daily:{petId}:{yyyyMMdd}}，
     * 由 pet-service PetMoodSnapshotScheduler 每晚写入。</p>
     *
     * @param petId     宠物 ID
     * @param weekStart 本周一
     * @param weekEnd   今天
     * @return 平均值（保留一位小数）；无快照则返回 null
     */
    private BigDecimal calculateAvgMood(Long petId, LocalDate weekStart, LocalDate weekEnd) {
        int sum = 0;
        int count = 0;

        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            String key = RedisKeyPrefix.PET_MOOD_DAILY_KEY + petId + ":" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            try {
                String moodStr = stringRedisTemplate.opsForValue().get(key);
                if (moodStr != null && !moodStr.isEmpty()) {
                    sum += Integer.parseInt(moodStr);
                    count++;
                }
            } catch (NumberFormatException e) {
                log.debug("心情快照值格式异常: key={}", key);
            }
        }

        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    /**
     * 生成中文摘要文本。
     */
    private String buildSummary(long feeds, long plays, long baths, int intimacyGain, String weekRange) {
        StringBuilder sb = new StringBuilder();
        sb.append("本周（").append(weekRange).append("）");

        long total = feeds + plays + baths;
        if (total == 0) {
            sb.append("暂无互动记录。");
        } else {
            sb.append("共互动 ").append(total).append(" 次（");
            if (feeds > 0) sb.append("喂食").append(feeds).append("次 ");
            if (plays > 0) sb.append("玩耍").append(plays).append("次 ");
            if (baths > 0) sb.append("洗澡").append(baths).append("次");
            sb.append("）。");
        }

        if (intimacyGain > 0) {
            sb.append("亲密度增长 ").append(intimacyGain).append("，继续保持哦~");
        } else if (intimacyGain < 0) {
            sb.append("亲密度下降 ").append(Math.abs(intimacyGain)).append("，要多陪陪它呀！");
        } else {
            sb.append("亲密度没有变化，多互动可以让宠物更亲近你~");
        }
        return sb.toString();
    }

    /**
     * 构建 reportJson 字符串（手动拼接，避免 Jackson 循环依赖）。
     */
    private String buildReportJson(String reportWeek, String weekRange,
                                   int totalFeeds, int totalPlays, int totalBaths,
                                   int intimacyGain, String summary) {
        return String.format(
                "{\"reportWeek\":\"%s\",\"weekRange\":\"%s\"," +
                        "\"totalFeeds\":%d,\"totalPlays\":%d,\"totalBaths\":%d," +
                        "\"intimacyGain\":%d,\"summary\":\"%s\"}",
                reportWeek, weekRange,
                totalFeeds, totalPlays, totalBaths,
                intimacyGain, escapeJson(summary));
    }

    /**
     * JSON 字符串转义。
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * 获取历史周报列表
     *
     * @param petId
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<WeeklyReport>> historyList(Long petId, Integer page, Integer size) {
        // 1.查询 pet_info 校验宠物属于当前用户，不匹配返回 403
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null || !petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.分页查询：SELECT report_id, report_week, week_range, intimacy_gain, create_time, update_time FROM weekly_report WHERE pet_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?
        // 2.1.构建分页条件
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);
        Page<WeeklyReport> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();

        // 2.2.查询数据库
        lambdaQuery()
                .eq(WeeklyReport::getPetId, petId)
                .page(sort);
        // 3.返回 PageDTO
        PageDTO<WeeklyReport> dto = PageDTO.of(sort);
        return Result.ok(dto);
    }
}
