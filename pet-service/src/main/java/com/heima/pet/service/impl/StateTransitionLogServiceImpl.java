package com.heima.pet.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heima.api.client.ShopClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.ProductEffectDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.*;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.pet.domain.po.PetAttribute;
import com.heima.pet.domain.po.PetInfo;
import com.heima.pet.domain.po.StateTransitionLog;
import com.heima.pet.domain.query.PageQuery;
import com.heima.pet.domain.redis.PetStateRedis;
import com.heima.pet.domain.vo.InteractResultVO;
import com.heima.pet.domain.vo.PetAttributeVO;
import com.heima.pet.domain.vo.PetEffectVO;
import com.heima.pet.domain.vo.StateTransitionVO;
import com.heima.pet.mapper.StateTransitionLogMapper;
import com.heima.pet.publisher.RedisStreamPublisher;
import com.heima.pet.service.IPetAttributeService;
import com.heima.pet.service.IPetInfoService;
import com.heima.pet.service.IStateTransitionLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.pet.service.PetStateEngine;
import com.heima.pet.utils.PetMessageUtil;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 状态流转日志表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Service
@RequiredArgsConstructor
public class StateTransitionLogServiceImpl extends ServiceImpl<StateTransitionLogMapper, StateTransitionLog> implements IStateTransitionLogService {

    private final IPetInfoService petInfoService;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private final IPetAttributeService petAttributeService;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ShopClient shopClient;

    private final RedisStreamPublisher redisStreamPublisher;

    /**
     * 与宠物互动（喂食 / 抚摸 / 洗澡）。
     * <p>
     * FEED / BATHE 必须传 productId（消耗背包道具）；PET 可选。
     * PET 不传 productId 时使用免费抚摸（每天限 {@link RedisKeyPrefix#PET_FREE_PET_DAILY_LIMIT} 次）。
     *
     * @param petId     宠物 ID
     * @param type      互动类型
     * @param productId 道具 ID（FEED/BATHE 必传，PET 可选）
     */
    @Override
    public Result<InteractResultVO> interact(Long petId, InteractionType type, Long productId) {
        // 验证用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1. 校验 type
        if (type == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), BizErrorCode.BAD_REQUEST.getMessage());
        }
        if (petId == null) {
            return Result.fail("宠物id不可为空");
        }

        // 2. 道具校验 & 消耗（在锁外完成，减少锁持有时间；若后续属性更新失败，道具已被消耗——毕设阶段可接受）
        int hungerEffect = 0, moodEffect = 0, cleanlinessEffect = 0;
        String usedProductName = null;
        boolean usedItem = false;   // 是否使用了背包道具
        boolean freePet = false;    // 是否免费抚摸

        if (type == InteractionType.FEED) {
            // 喂食必须传道具
            if (productId == null) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "喂食需要选择食物哦~ 去商城购买吧！");
            }
            ProductEffectDTO effect = shopClient.consumeItem(userId, productId);
            if (effect == null || effect.getEffectType() == null) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "这个道具不能使用哦~");
            }
            if (effect.getEffectType() != InteractionType.FEED) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "这不是食物哦~ 请选择食物类道具");
            }
            hungerEffect = effect.getEffectHunger() != null ? effect.getEffectHunger() : 0;
            moodEffect = effect.getEffectMood() != null ? effect.getEffectMood() : 0;
            cleanlinessEffect = effect.getEffectCleanliness() != null ? effect.getEffectCleanliness() : 0;
            usedProductName = effect.getProductName();
            usedItem = true;

        } else if (type == InteractionType.BATHE) {
            // 洗澡必须传道具
            if (productId == null) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "洗澡需要洗浴用品哦~ 去商城购买吧！");
            }
            ProductEffectDTO effect = shopClient.consumeItem(userId, productId);
            if (effect == null || effect.getEffectType() == null) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "这个道具不能使用哦~");
            }
            if (effect.getEffectType() != InteractionType.BATHE) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "这不是洗浴用品哦~ 请选择洗浴类道具");
            }
            hungerEffect = effect.getEffectHunger() != null ? effect.getEffectHunger() : 0;
            moodEffect = effect.getEffectMood() != null ? effect.getEffectMood() : 0;
            cleanlinessEffect = effect.getEffectCleanliness() != null ? effect.getEffectCleanliness() : 0;
            usedProductName = effect.getProductName();
            usedItem = true;

        } else if (type == InteractionType.PET) { // PET
            if (productId != null) {
                // 使用玩具道具
                ProductEffectDTO effect = shopClient.consumeItem(userId, productId);
                if (effect == null || effect.getEffectType() == null) {
                    return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "这个道具不能使用哦~");
                }
                if (effect.getEffectType() != InteractionType.PET) {
                    return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "这不是玩具哦~ 请选择玩具类道具");
                }
                moodEffect = effect.getEffectMood() != null ? effect.getEffectMood() : 0;
                hungerEffect = effect.getEffectHunger() != null ? effect.getEffectHunger() : 0;
                cleanlinessEffect = effect.getEffectCleanliness() != null ? effect.getEffectCleanliness() : 0;
                usedProductName = effect.getProductName();
                usedItem = true;
            } else {
                // 免费抚摸：检查今日剩余次数
                String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String freeKey = RedisKeyPrefix.PET_FREE_PET_KEY + userId + ":" + petId + ":" + today;
                String countStr = stringRedisTemplate.opsForValue().get(freeKey);
                int freeCount = countStr != null ? Integer.parseInt(countStr) : 0;
                if (freeCount >= RedisKeyPrefix.PET_FREE_PET_DAILY_LIMIT) {
                    return Result.fail(BizErrorCode.BAD_REQUEST.getCode(),
                            "今日免费抚摸次数已用完（" + RedisKeyPrefix.PET_FREE_PET_DAILY_LIMIT + "次/天），去商城买个玩具吧~");
                }
                moodEffect = PetStateEngine.PET_MOOD;  // 默认 +10
                freePet = true;
            }
        } else {
            // 互动类型不在枚举类
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "互动类型错误");
        }

        // 3. 查询宠物信息
        PetInfo petInfo = petInfoService.getById(petId);
        if (petInfo == null) {
            return Result.fail("宠物id不存在");
        }
        if (petInfo.getState().equals(PetState.RUNAWAY)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "宠物已离家出走");
        }
        PetState beforeState = petInfo.getState();

        PetStateRedis petStateRedis = new PetStateRedis();
        petStateRedis.setState(petInfo.getState());
        PetAttribute petAttribute = petAttributeService.lambdaQuery()
                .eq(PetAttribute::getPetId, petId)
                .one();
        BeanUtil.copyProperties(petAttribute, petStateRedis);

        PetAttributeVO petAttributeVO = new PetAttributeVO();
        PetEffectVO petEffectVO = new PetEffectVO();

        // 4. 获取分布式锁
        RLock lock = redissonClient.getLock(RedisKeyPrefix.PET_LOCK_INTERACT_KEY + petId);
        PetState evaluateState = beforeState;
        try {
            boolean isLock = lock.tryLock(RedisKeyPrefix.PET_LOCK_INTERACT_WAIT_TIME,
                    RedisKeyPrefix.PET_LOCK_INTERACT_LEASE_TIME, TimeUnit.SECONDS);
            if (!isLock) {
                return Result.fail(BizErrorCode.TOO_MANY_REQUESTS.getCode(), "互动太频繁了，请稍后再试");
            }

            // 5. 应用效果
            Integer afterHunger = stateNumberChange(petStateRedis.getHunger(), hungerEffect);
            Integer afterMood = stateNumberChange(petStateRedis.getMood(), moodEffect);
            Integer afterCleanliness = stateNumberChange(petStateRedis.getCleanliness(), cleanlinessEffect);

            petEffectVO.setHungerChange(afterHunger - petStateRedis.getHunger());
            petEffectVO.setMoodChange(afterMood - petStateRedis.getMood());
            petEffectVO.setCleanlinessChange(afterCleanliness - petStateRedis.getCleanliness());

            // 6. 更新 DB pet_attribute
            petAttributeService.lambdaUpdate()
                    .set(PetAttribute::getHunger, afterHunger)
                    .set(PetAttribute::getMood, afterMood)
                    .set(PetAttribute::getCleanliness, afterCleanliness)
                    .set(PetAttribute::getUpdateTime, LocalDateTime.now())
                    .eq(PetAttribute::getPetId, petId)
                    .update();

            // 7. 判断状态变化
            evaluateState = PetStateEngine.evaluateState(afterHunger, afterMood, afterCleanliness, beforeState);
            petAttributeVO.setHunger(afterHunger);
            petAttributeVO.setMood(afterMood);
            petAttributeVO.setCleanliness(afterCleanliness);

            // 8. 写回 Redis
            String stateKey = RedisKeyPrefix.PET_STATE_KEY + petId;
            Map<String, String> redisUpdate = new java.util.HashMap<>();
            redisUpdate.put("hunger", String.valueOf(afterHunger));
            redisUpdate.put("mood", String.valueOf(afterMood));
            redisUpdate.put("cleanliness", String.valueOf(afterCleanliness));
            if (!beforeState.equals(evaluateState)) {
                redisUpdate.put("state", evaluateState.name());
                redisUpdate.put("stateSince", String.valueOf(System.currentTimeMillis()));
            }
            stringRedisTemplate.opsForHash().putAll(stateKey, redisUpdate);

            // 9. 状态变化 → 更新 pet_info + 写日志
            if (!beforeState.equals(evaluateState)) {
                petInfoService.updateById(PetInfo.builder()
                        .state(evaluateState)
                        .updateTime(LocalDateTime.now())
                        .id(petId)
                        .build());

                String description = evaluatePetState(afterHunger, afterMood, afterCleanliness);
                long logId = snowflakeIdGenerator.nextId();
                StateTransitionLog stateTransitionLog = StateTransitionLog.builder()
                        .id(logId)
                        .petId(petId)
                        .fromState(beforeState)
                        .toState(evaluateState)
                        .triggerType(TriggerType.INTERACT)
                        .description(description)
                        .createTime(LocalDateTime.now())
                        .build();
                save(stateTransitionLog);
            }

            // 10. 免费抚摸消耗次数（成功后才 INCR）
            if (freePet) {
                String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String freeKey = RedisKeyPrefix.PET_FREE_PET_KEY + userId + ":" + petId + ":" + today;
                stringRedisTemplate.opsForValue().increment(freeKey);
                // 首次使用时设过期到当天 midnight
                Long ttl = stringRedisTemplate.getExpire(freeKey);
                if (ttl == null || ttl <= 0) {
                    LocalDateTime midnight = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT);
                    long secondsToMidnight = ChronoUnit.SECONDS.between(LocalDateTime.now(), midnight);
                    stringRedisTemplate.expire(freeKey, secondsToMidnight, TimeUnit.SECONDS);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 11. 发布 Stream 事件（锁外发布，不影响主流程）
        int scoreChange = freePet ? 2 : 5;  // 免费抚摸亲密度减半
        String eventId = snowflakeIdGenerator.nextId() + "";
        EventType eventType;
        if (type.equals(InteractionType.FEED)) {
            eventType = EventType.FEED;
        } else if (type.equals(InteractionType.PET)) {
            eventType = EventType.PLAY;
        } else {
            eventType = EventType.BATHE;
        }
        redisStreamPublisher.publishInteractEvent(
                eventId, eventType.name(), userId, petId,
                usedItem ? productId : null, scoreChange,
                petEffectVO.getHungerChange(), petEffectVO.getMoodChange(), petEffectVO.getCleanlinessChange(),
                beforeState.name(), evaluateState.name());

        // 12. 生成返回
        String cuteMessage = PetMessageUtil.getRandomMessage(petInfo.getSpecies(),
                Objects.requireNonNull(Personality.nextString(petInfo.getPersonality())), type);
        if (usedItem && usedProductName != null) {
            cuteMessage = "使用了" + usedProductName + "，" + cuteMessage;
        }

        InteractResultVO build = InteractResultVO.builder()
                .type(type)
                .typeName(type.getDesc())
                .beforeState(beforeState)
                .afterState(evaluateState)
                .stateChanged(BooleanUtil.isFalse(beforeState.equals(evaluateState)))
                .attributes(petAttributeVO)
                .effects(petEffectVO)
                .cuteMessage(cuteMessage)
                .build();
        return Result.ok(build);
    }

    /**
     * 根据状态值返回描述
     *
     * @param afterHunger
     * @param afterMood
     * @param afterCleanliness
     * @return
     */
    private String evaluatePetState(Integer afterHunger, Integer afterMood, Integer afterCleanliness) {
        if (afterCleanliness < PetStateEngine.CRITICAL_THRESHOLD) {
            return "清洁值降至" + afterCleanliness + "，低于阈值" + PetStateEngine.CRITICAL_THRESHOLD;
        } else if (afterMood < PetStateEngine.CRITICAL_THRESHOLD) {
            return "心情值降至" + afterMood + "，低于阈值" + PetStateEngine.CRITICAL_THRESHOLD;
        } else if (afterHunger < PetStateEngine.CRITICAL_THRESHOLD) {
            return "饥饿值降至" + afterHunger + "，低于阈值" + PetStateEngine.CRITICAL_THRESHOLD;
        } else if (afterCleanliness >= PetStateEngine.RECOVERY_THRESHOLD && afterMood >= PetStateEngine.RECOVERY_THRESHOLD && afterHunger >= PetStateEngine.RECOVERY_THRESHOLD) {
            return "宠物一切正常";
        } else {
            return "宠物状态一般";
        }
    }

    private Integer stateNumberChange(Integer stateNum, int changeNum) {
        return stateNum <= 100 - changeNum ? stateNum + changeNum : 100;
    }

    /**
     * 获取状态变更历史
     *
     * @param petId
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<StateTransitionVO>> logList(Long petId, Long page, Long size) {
        // 1.查询 pet_info 校验宠物是否属于当前用户（user_id 匹配），不匹配返回 403；不存在返回 404
        // 1.1.校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1.2.检验petId是否存在
        if (petId == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), BizErrorCode.BAD_REQUEST.getMessage());
        }
        // 1.2.查询数据库pet-info
        PetInfo petInfo = petInfoService.getById(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.NOT_FOUND.getMessage());
        }
        if (!petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.分页查询：SELECT * FROM state_transition_log WHERE pet_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?
        // 2.1.构建分页条件
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page.intValue());
        pageQuery.setPageSize(size == null ? 5 : size.intValue());
        pageQuery.setSortBy("create_time");
        pageQuery.setIsAsc(false);
        Page<StateTransitionLog> logPage = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        // 2.2.分页查询
        lambdaQuery()
                .eq(StateTransitionLog::getPetId, petId)
                .page(logPage);
        // 3.查询总数：SELECT COUNT(*) FROM state_transition_log WHERE pet_id=?
        // 上述已经得出
        // 4.将枚举编码映射为中文名（fromStateName, toStateName, triggerTypeName）
        PageDTO<StateTransitionVO> pageDTO = PageDTO.of(logPage, StateTransitionVO.class);
        for (StateTransitionVO record : pageDTO.getRecords()) {
            record.setFromStateName(record.getFromState().getDesc());
            record.setToStateName(record.getToState().getDesc());
            record.setTriggerTypeName(record.getTriggerType().getDesc());
        }
        // 5.返回 PageDTO
        return Result.ok(pageDTO);
    }

    /**
     * 扣减目标宠物心情值 -5
     *
     * @param petId
     * @return
     */
    @Transactional
    @Override
    public boolean deductMoodByPetId(Long petId) {
        // 根据id查询当前属性值
        // 从redis中查询
        String stateKey = RedisKeyPrefix.PET_STATE_KEY + petId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(stateKey);

        // 全局定义
        Integer afterMood;
        Integer hunger;
        Integer cleanliness;
        PetState state;

        if (entries.isEmpty()) {
            // 数据库查询
            PetInfo petInfo = petInfoService.getById(petId);
            PetAttribute petAttribute = petAttributeService.lambdaQuery()
                    .eq(PetAttribute::getPetId, petId)
                    .one();

            if (petAttribute == null || petInfo == null) {
                return false;
            }

            afterMood = petAttribute.getMood() - 5;
            hunger = petAttribute.getHunger();
            cleanliness = petAttribute.getCleanliness();
            state = petInfo.getState();
        } else {
            // 非空，转换类型
            PetStateRedis petStateRedis = BeanUtil.fillBeanWithMap(entries, new PetStateRedis(), false);
            afterMood = petStateRedis.getMood();
            hunger = petStateRedis.getHunger();
            cleanliness = petStateRedis.getCleanliness();
            state = petStateRedis.getState();
        }

        PetState evaluateState = PetStateEngine.evaluateState(hunger, afterMood, cleanliness, state);

        // 状态变化，更新日志
        if (!evaluateState.equals(state)) {
            // 增加日志
            String description = evaluatePetState(hunger, afterMood, cleanliness);
            long logId = snowflakeIdGenerator.nextId();
            StateTransitionLog stateTransitionLog = StateTransitionLog.builder()
                    .id(logId)
                    .petId(petId)
                    .fromState(state)
                    .toState(evaluateState)
                    .triggerType(TriggerType.MANUAL)
                    .description(description)
                    .createTime(LocalDateTime.now())
                    .build();

            save(stateTransitionLog);

            // 更新redis
            stringRedisTemplate.opsForHash().put(stateKey, "state", evaluateState);

            // 更新pet-info表格状态
            petInfoService.lambdaUpdate()
                    .set(PetInfo::getState, evaluateState)
                    .set(PetInfo::getUpdateTime, LocalDateTime.now())
                    .eq(PetInfo::getId, petId)
                    .update();
        }
        // 执行更新
        petAttributeService.lambdaUpdate()
                .set(PetAttribute::getMood, afterMood)
                .set(PetAttribute::getUpdateTime, LocalDateTime.now())
                .eq(PetAttribute::getPetId, petId)
                .update();
        return true;
    }
}
