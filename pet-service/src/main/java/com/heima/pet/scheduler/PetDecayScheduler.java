package com.heima.pet.scheduler;

import cn.hutool.core.bean.BeanUtil;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.enums.PetState;
import com.heima.api.enums.TriggerType;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.pet.domain.po.PetAttribute;
import com.heima.pet.domain.po.PetInfo;
import com.heima.pet.domain.po.StateTransitionLog;
import com.heima.pet.domain.redis.PetStateRedis;
import com.heima.pet.publisher.RedisStreamPublisher;
import com.heima.pet.service.IPetAttributeService;
import com.heima.pet.service.IPetInfoService;
import com.heima.pet.service.IStateTransitionLogService;
import com.heima.pet.service.PetStateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 宠物属性定时衰减 — 每分钟执行一次。
 *
 * <h3>做什么</h3>
 * <ol>
 *   <li>扫描 Redis 中所有 pet:status:* key</li>
 *   <li>计算距上次衰减的分钟数</li>
 *   <li>按系数扣减 hunger/mood/cleanliness</li>
 *   <li>调用 PetStateEngine 判断状态变化</li>
 *   <li>若触发离家出走条件 → 进入 RUNAWAY</li>
 *   <li>更新 Redis Hash + DB pet_attribute</li>
 *   <li>状态变化时写 state_transition_log + 发布 Stream 事件</li>
 * </ol>
 *
 * <h3>并发控制</h3>
 * 每个宠物独立 Redisson 锁 {@code pet:lock:decay:{petId}}，
 * 避免衰减和用户互动同时修改属性。
 *
 * @author lucy
 * @since 2026-07-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetDecayScheduler {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final IPetInfoService petInfoService;
    private final IPetAttributeService petAttributeService;
    private final IStateTransitionLogService stateTransitionLogService;
    private final RedisStreamPublisher redisStreamPublisher;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 每分钟执行一次衰减。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void applyDecay() {
        log.info("========== 宠物属性衰减任务触发 ==========");
        try {
            // 1. 扫描所有 pet:status:* key
            Set<String> keys = stringRedisTemplate.keys(RedisKeyPrefix.PET_STATE_KEY + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            log.debug("扫描到 {} 个宠物状态缓存", keys.size());

            int decayedCount = 0;
            for (String key : keys) {
                try {
                    if (decaySinglePet(key)) {
                        decayedCount++;
                    }
                } catch (Exception e) {
                    log.error("衰减宠物失败: key={}", key, e);
                }
            }
            if (decayedCount > 0) {
                log.info("衰减完成: {} 个宠物属性已更新", decayedCount);
            }
        } catch (Exception e) {
            log.error("衰减任务异常", e);
        }
    }

    /**
     * 对单个宠物执行衰减。
     *
     * @return true 表示执行了衰减（含状态变化），false 表示跳过
     */
    private boolean decaySinglePet(String stateKey) {
        // 提取 petId
        String petIdStr = stateKey.substring(RedisKeyPrefix.PET_STATE_KEY.length());
        Long petId;
        try {
            petId = Long.parseLong(petIdStr);
        } catch (NumberFormatException e) {
            log.warn("无法解析 petId: {}", stateKey);
            return false;
        }

        RLock lock = redissonClient.getLock(RedisKeyPrefix.PET_LOCK_DECAY_KEY + petId);
        try {
            // 尝试获取锁（不等，拿不到就跳过，下次衰减再处理）
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                return false;
            }

            // 1. 读 Redis 当前状态
            Map<Object, Object> stateMap = stringRedisTemplate.opsForHash().entries(stateKey);
            if (stateMap.isEmpty()) {
                return false;
            }
            PetStateRedis stateRedis = BeanUtil.fillBeanWithMap(stateMap, new PetStateRedis(), false);

            // 2. 读 DB 上次衰减时间
            PetAttribute petAttribute = petAttributeService.lambdaQuery()
                    .eq(PetAttribute::getPetId, petId)
                    .one();
            if (petAttribute == null) return false;

            long elapsedMinutes = ChronoUnit.MINUTES.between(petAttribute.getLastDecayTime(), LocalDateTime.now());
            if (elapsedMinutes < 5) {
                return false;  // 不到 1 分钟，跳过
            }

            // 3. 计算衰减（整数系数 × 整数分钟 → 无小数，直接 int 运算）
            int[] decayed = PetStateEngine.applyDecay(
                    stateRedis.getHunger(), stateRedis.getMood(),
                    stateRedis.getCleanliness(), elapsedMinutes);
            int afterHunger = decayed[0];
            int afterMood = decayed[1];
            int afterCleanliness = decayed[2];

            PetState currentState = stateRedis.getState();
            PetState beforeState = currentState;

            // 4. 状态判定：已离家出走的保持离家出走，仅衰减属性
            final PetState newState;
            final boolean runaway;
            if (currentState == PetState.RUNAWAY) {
                newState = PetState.RUNAWAY;
                runaway = false;
            } else {
                PetState baseState = PetStateEngine.evaluateBaseState(afterHunger, afterMood, afterCleanliness);
                runaway = PetStateEngine.shouldRunaway(baseState, stateRedis.getStateSince());
                newState = runaway ? PetState.RUNAWAY : baseState;
            }

            // 5. 更新 Redis Hash
            Map<String, String> updateMap = new HashMap<>();
            updateMap.put("hunger", String.valueOf(afterHunger));
            updateMap.put("mood", String.valueOf(afterMood));
            updateMap.put("cleanliness", String.valueOf(afterCleanliness));
            if (!currentState.equals(newState)) {
                updateMap.put("state", newState.name());
                updateMap.put("stateSince", String.valueOf(System.currentTimeMillis()));
            }
            stringRedisTemplate.opsForHash().putAll(stateKey, updateMap);

            // 6. 更新 DB pet_attribute
            PetInfo petInfo = petInfoService.getById(petId);
            petAttributeService.lambdaUpdate()
                    .set(PetAttribute::getHunger, afterHunger)
                    .set(PetAttribute::getMood, afterMood)
                    .set(PetAttribute::getCleanliness, afterCleanliness)
                    .set(PetAttribute::getLastDecayTime, LocalDateTime.now())
                    .set(PetAttribute::getUpdateTime, LocalDateTime.now())
                    .eq(PetAttribute::getPetId, petId)
                    .update();

            // 7. 状态变化 → 写日志 + 发布事件 + 更新 pet_info
            if (!beforeState.equals(newState)) {
                String description = buildDecayDescription(
                        beforeState, newState,
                        currentState.equals(newState) ? null : "离家出走",
                        afterHunger, afterMood, afterCleanliness);

                long logId = snowflakeIdGenerator.nextId();
                StateTransitionLog logEntry = StateTransitionLog.builder()
                        .id(logId)
                        .petId(petId)
                        .fromState(beforeState)
                        .toState(newState)
                        .triggerType(runaway ? TriggerType.DECAY : TriggerType.DECAY)
                        .description(description)
                        .createTime(LocalDateTime.now())
                        .build();
                stateTransitionLogService.save(logEntry);

                // 更新 pet_info 的状态（离家出走还记录时间）
                if (newState == PetState.RUNAWAY) {
                    petInfoService.lambdaUpdate()
                            .set(PetInfo::getState, newState)
                            .set(PetInfo::getRunawayTime, LocalDateTime.now())
                            .set(PetInfo::getUpdateTime, LocalDateTime.now())
                            .eq(PetInfo::getId, petId)
                            .update();
                } else {
                    petInfoService.lambdaUpdate()
                            .set(PetInfo::getState, newState)
                            .set(PetInfo::getUpdateTime, LocalDateTime.now())
                            .eq(PetInfo::getId, petId)
                            .update();
                }

                // 发布 Stream 事件
                int scoreChange = newState == PetState.RUNAWAY ? -50 : -10;
                redisStreamPublisher.publishStateChangeEvent(
                        String.valueOf(logId),
                        petInfo != null ? petInfo.getUserId() : 0L,
                        petId, scoreChange,
                        beforeState.name(), newState.name(),
                        runaway ? "RUNAWAY" : "DECAY");
            }

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String buildDecayDescription(PetState before, PetState after,
                                         String reason,
                                         int hunger, int mood, int cleanliness) {
        if (after == PetState.RUNAWAY) {
            return "宠物处于" + before.getDesc() + "状态过久，离家出走了";
        }
        return String.format("属性衰减：饥饿=%d 心情=%d 清洁=%d → 状态 %s→%s",
                hunger, mood, cleanliness, before.getDesc(), after.getDesc());
    }
}
