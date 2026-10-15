package com.heima.analytics.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heima.analytics.domain.po.IntimacyEvent;
import com.heima.analytics.domain.vo.*;
import com.heima.analytics.mapper.IntimacyEventMapper;
import com.heima.analytics.service.IIntimacyEventService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.api.client.AuthClient;
import com.heima.api.client.PetClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.po.UserInfo;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.EventType;
import com.heima.common.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 亲密度事件流水表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntimacyEventServiceImpl extends ServiceImpl<IntimacyEventMapper, IntimacyEvent> implements IIntimacyEventService {

    private final StringRedisTemplate stringRedisTemplate;

    private final AuthClient authClient;

    private final PetClient petClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * 亲密度排行榜（Top N）。
     *
     * <h3>排名规则</h3>
     * <ul>
     *   <li>按亲密度降序排列</li>
     *   <li><b>并列处理</b>：分数相同时共享同一 rank（如两个 100 分都是第 1 名，
     *       下一个 90 分是第 3 名而非第 2 名）</li>
     *   <li>已删除的宠物/用户自动跳过，并从 ZSet 中清理</li>
     * </ul>
     *
     * <h3>缓存策略</h3>
     * 优先读缓存（TTL 60s），未命中再查 ZSet + DB 并回写缓存。
     *
     * @param limit 返回条数，默认 20，最大 100
     */
    @Override
    public Result<List<IntimacyTopVO>> top(Integer limit) {
        // 校验用户是否登录
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }

        // 1. 解析 limit 参数，上限 100
        if (limit == null) {
            limit = 20;
        }
        if (limit > 100) {
            limit = 100;
        }

        // 2. 优先读缓存
        String cacheKey = RedisKeyPrefix.INTIMACY_LEADERBOARD_CACHE_KEY + limit;
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isEmpty()) {
                IntimacyTopVO[] cached = objectMapper.readValue(cachedJson, IntimacyTopVO[].class);
                return Result.ok(Arrays.asList(cached));
            }
        } catch (Exception e) {
            // 缓存读取失败（如 JSON 格式异常），降级查 DB，不阻塞
            log.warn("排行榜缓存读取失败，降级为直接查询: limit={}", limit, e);
        }

        // 3. ZREVRANGE intimacy:leaderboard 0 {limit-1} WITHSCORES
        //    注意：返回 LinkedHashSet，迭代顺序 = 分数降序
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, 0L, limit.longValue() - 1);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 4. 解析 ZSet 成员，保持 ZSet 的顺序（分数降序）
        //    成员格式: "userId:petId"
        //    使用 List<MemberScore> 维护 ZSet 原始顺序（不能用 Map，会丢序）
        record MemberScore(Long userId, Long petId, double score) {
        }
        List<MemberScore> memberScores = new ArrayList<>(typedTuples.size());

        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            String value = tuple.getValue();
            if (value == null || !value.contains(":")) {
                continue;
            }
            int colonIdx = value.indexOf(":");
            try {
                Long uid = Long.parseLong(value.substring(0, colonIdx));
                Long pid = Long.parseLong(value.substring(colonIdx + 1));
                memberScores.add(new MemberScore(uid, pid, tuple.getScore()));
            } catch (NumberFormatException e) {
                log.warn("ZSet member 格式异常，跳过: {}", value);
            }
        }

        if (memberScores.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 5. 批量查询宠物信息和用户信息，构建查找 Map
        List<Long> petIds = memberScores.stream().map(MemberScore::petId).distinct().toList();
        Map<Long, PetInfo> petInfoMap;
        try {
            List<PetInfo> petInfos = petClient.queryPetByIds(petIds);
            petInfoMap = petInfos.stream()
                    .collect(Collectors.toMap(PetInfo::getId, Function.identity()));
        } catch (Exception e) {
            log.error("Feign 调用 petClient.queryPetByIds 失败", e);
            return Result.fail(500, "查询宠物信息失败");
        }

        List<Long> existingUserIds = petInfoMap.values().stream()
                .map(PetInfo::getUserId).distinct().toList();
        Map<Long, UserInfo> userInfoMap;
        try {
            if (!existingUserIds.isEmpty()) {
                List<UserInfo> userInfos = authClient.queryUserByIds(existingUserIds);
                userInfoMap = userInfos.stream()
                        .collect(Collectors.toMap(UserInfo::getId, Function.identity()));
            } else {
                userInfoMap = Collections.emptyMap();
            }
        } catch (Exception e) {
            log.error("Feign 调用 authClient.queryUserByIds 失败", e);
            return Result.fail(500, "查询用户信息失败");
        }

        // 6. 组装排行榜 — 按 ZSet 原始顺序迭代，处理并列排名
        List<IntimacyTopVO> voList = new ArrayList<>();
        int visibleIndex = 0;        // 可见条目的序号（被跳过的条目不计入）
        Double prevScore = null;     // 上一条的分数，用于判断并列

        for (MemberScore ms : memberScores) {
            // 查找宠物信息，不存在说明已删除 — 跳过并从 ZSet 清理
            PetInfo petInfo = petInfoMap.get(ms.petId());
            if (petInfo == null) {
                log.debug("宠物已删除，从 ZSet 中移除: {}:{}", ms.userId(), ms.petId());
                stringRedisTemplate.opsForZSet().remove(
                        RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY,
                        ms.userId() + ":" + ms.petId());
                continue;
            }

            // 查找用户信息，不存在说明已删除 — 跳过
            UserInfo userInfo = userInfoMap.get(ms.userId());
            if (userInfo == null) {
                log.debug("用户已删除，跳过排行榜条目: userId={}", ms.userId());
                continue;
            }

            visibleIndex++;

            // ---- 并列排名处理 ----
            // 分数相同时共享 rank，分数变化时 rank 跳到当前 visibleIndex
            int rank;
            if (prevScore == null || ms.score() != prevScore) {
                rank = visibleIndex;          // 新分数 → rank 跳到当前位置
            } else {
                rank = voList.get(voList.size() - 1).getRank();  // 沿用上一条的 rank
            }

            IntimacyTopVO vo = new IntimacyTopVO();
            vo.setRank(rank);
            vo.setUserId(userInfo.getId());
            vo.setUsername(userInfo.getUsername());
            vo.setPetId(petInfo.getId());
            vo.setPetName(petInfo.getPetName());
            vo.setSpecies(petInfo.getSpecies());
            vo.setIntimacy((int) ms.score());

            voList.add(vo);
            prevScore = ms.score();
        }

        // 7. 写缓存（TTL 60s）
        try {
            String json = objectMapper.writeValueAsString(voList);
            stringRedisTemplate.opsForValue().set(
                    cacheKey, json,
                    RedisKeyPrefix.INTIMACY_LEADERBOARD_CACHE_TTL, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("排行榜缓存写入失败", e);
            // 缓存写失败不阻塞主流程
        }

        return Result.ok(voList);
    }

    /**
     * 我的宠物排名。
     *
     * <h3>排名规则</h3>
     * 使用 <b>并列排名</b>（与 {@link #top(Integer)} 一致）：
     * rank = 分数严格高于当前宠物的成员数 + 1。
     * 例如分数 [100, 100, 90]：两个 100 分并列第 1，90 分是第 3 名。
     *
     * <h3>未上榜</h3>
     * 宠物从未产生过亲密度事件时，ZSet 中无此成员，
     * 返回 rank=0, intimacy=0, percentile="暂无排名"。
     *
     * @param petId 宠物 ID
     */
    @Override
    public Result<RankVO> rank(Long petId) {
        // 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }

        // 1. 校验宠物存在且属于当前用户
        PetInfo petInfo;
        try {
            petInfo = petClient.getBasicPetInfo(petId);
        } catch (Exception e) {
            log.error("Feign 调用 petClient.getBasicPetInfo 失败: petId={}", petId, e);
            return Result.fail(500, "查询宠物信息失败");
        }
        if (petInfo == null) {
            return Result.fail(404, "宠物不存在");
        }
        if (!petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }

        // 2. ZSCORE 获取亲密度值 + ZCARD 获取总参与数
        String member = userId + ":" + petId;
        Double score = stringRedisTemplate.opsForZSet()
                .score(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, member);
        Long total = stringRedisTemplate.opsForZSet()
                .size(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY);

        // ZCARD 对不存在的 key 返回 null（不是 0），需要兜底
        int totalCount = total != null ? total.intValue() : 0;

        RankVO rankVO = new RankVO();
        rankVO.setPetId(petId);
        rankVO.setPetName(petInfo.getPetName());
        rankVO.setTotal(totalCount);

        // 3. 若宠物从未参与排行
        if (score == null) {
            rankVO.setRank(0);
            rankVO.setIntimacy(0);
            rankVO.setPercentile("暂无排名");
            return Result.ok(rankVO);
        }

        // 4. 并列排名：rank = 分数严格高于当前分数的成员数 + 1
        //    使用 ZCOUNT key (score+1) +inf 计数（ZCOUNT 是闭区间，score+1 排除同分）
        Long higherCount = stringRedisTemplate.opsForZSet()
                .count(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, score + 1, Double.POSITIVE_INFINITY);
        long rank = (higherCount != null ? higherCount : 0) + 1;

        // 5. 计算百分位
        String percentile;
        if (totalCount > 0) {
            double pct = (double) rank / totalCount * 100;
            percentile = String.format("前 %.1f%%", pct);
        } else {
            percentile = "暂无排名";
        }

        rankVO.setRank((int) rank);
        rankVO.setIntimacy(score.intValue());
        rankVO.setPercentile(percentile);
        return Result.ok(rankVO);
    }

    /**
     * 宠物亲密度详情
     *
     * @param petId
     * @return
     */
    @Override
    public Result<IntimacyDetailVO> detail(Long petId) {
        // 1.查询 pet_info 校验宠物属于当前用户，不匹配返回 403
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null || !petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.ZSCORE intimacy:leaderboard {userId}:{petId} 获取总亲密度值
        String member = userId + ":" + petId;
        Double score = stringRedisTemplate.opsForZSet().score(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, member);
        if (score == null) {
            score = 0d;
        }
        // 3.查询最近 intimacy_event 记录：SELECT * FROM intimacy_event WHERE pet_id=? ORDER BY create_time DESC LIMIT 10
        List<IntimacyEvent> eventList = lambdaQuery()
                .eq(IntimacyEvent::getPetId, petId)
                .orderByDesc(IntimacyEvent::getCreateTime)
                .last("LIMIT 10")
                .list();
        // 4.按 event_type 分组聚合计算 breakdown（feedIntimacy, playIntimacy, bathIntimacy, penaltyIntimacy）
        // 分组求和（按 eventType）
        Map<EventType, Integer> breakdownMap = eventList.stream()
                .collect(Collectors.groupingBy(
                        IntimacyEvent::getEventType, // 或直接 event.getEventType()
                        Collectors.summingInt(IntimacyEvent::getScoreChange)
                ));

        // 提取各项（若某项不存在，默认为0）
        int feedIntimacy = breakdownMap.getOrDefault(EventType.FEED, 0);
        int playIntimacy = breakdownMap.getOrDefault(EventType.PLAY, 0);
        int bathIntimacy = breakdownMap.getOrDefault(EventType.BATHE, 0);
        int penaltyIntimacy = breakdownMap.getOrDefault(EventType.STEALED, 0)
                + breakdownMap.getOrDefault(EventType.STATE_CHANGE, 0);
        // 5.组装 IntimacyDetailVO 返回
        Breakdown breakdown = Breakdown.builder()
                .feedIntimacy(feedIntimacy)
                .playIntimacy(playIntimacy)
                .bathIntimacy(bathIntimacy)
                .penaltyIntimacy(penaltyIntimacy)
                .build();

        IntimacyDetailVO detailVO = new IntimacyDetailVO();
        detailVO.setPetId(petId);
        detailVO.setPetName(petInfo.getPetName());
        detailVO.setIntimacy(score.intValue());
        detailVO.setBreakdown(breakdown);

        if (eventList.isEmpty()) {
            detailVO.setRecentEvents(Collections.emptyList());
            return Result.ok(detailVO);
        }

        List<RecentEvents> list = BeanUtil.copyToList(eventList, RecentEvents.class);
        List<RecentEvents> recentEventsList = list.stream()
                .peek(r -> r.setEventName(r.getEventType().getDesc()))
                .toList();

        detailVO.setRecentEvents(recentEventsList);
        return Result.ok(detailVO);
    }
}
