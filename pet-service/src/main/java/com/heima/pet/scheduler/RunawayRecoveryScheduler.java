package com.heima.pet.scheduler;

import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.enums.PetState;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.pet.domain.po.PetInfo;
import com.heima.pet.domain.po.StateTransitionLog;
import com.heima.pet.publisher.RedisStreamPublisher;
import com.heima.pet.service.IPetInfoService;
import com.heima.pet.service.IStateTransitionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 离家出走超时兜底扫描 — 每 5 分钟扫描超时未找回的宠物，自动解除领养。
 *
 * <h3>背景</h3>
 * <pre>
 * 宠物离家出走（RUNAWAY）后，用户有 7 天窗口通过 3.5 接口主动找回。
 * 7 天超时 → 自动解除领养（软删除 pet_info）。
 *
 * 为什么用定时扫描而不是 Redis Keyspace 通知？
 *   - Keyspace Notification 在集群/哨兵中不可靠
 *   - DB 的 runaway_time 字段是可靠的"单点真相"
 *   - 最多延迟 5 分钟，毕设场景完全可接受
 * </pre>
 *
 * @author lucy
 * @since 2026-07-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunawayRecoveryScheduler {

    private final IPetInfoService petInfoService;
    private final IStateTransitionLogService stateTransitionLogService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisStreamPublisher redisStreamPublisher;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 每 5 分钟扫描一次离家出走超时的宠物。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void scanAndCancelRunawayPets() {
        log.info("========== 离家出走超时扫描触发 ==========");
        try {
            // 查询所有离家出走超过 30 分钟的宠物
            LocalDateTime threshold = LocalDateTime.now().minusDays(7);
            List<PetInfo> timeoutPets = petInfoService.lambdaQuery()
                    .eq(PetInfo::getState, PetState.RUNAWAY)
                    .lt(PetInfo::getRunawayTime, threshold)
                    .eq(PetInfo::getDeleted, 0)
                    .list();

            if (timeoutPets.isEmpty()) {
                return;
            }

            log.info("扫描到 {} 只离家出走超时的宠物，开始自动解除领养", timeoutPets.size());

            for (PetInfo pet : timeoutPets) {
                try {
                    cancelAdoption(pet);
                } catch (Exception e) {
                    log.error("自动解除领养失败: petId={}", pet.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("离家出走超时扫描异常", e);
        }
    }

    /**
     * 自动解除领养：软删除 pet_info + 清理 Redis + 记录日志 + 发布事件。
     */
    private void cancelAdoption(PetInfo pet) {
        Long petId = pet.getId();

        // 1. 软删除
        petInfoService.lambdaUpdate()
                .set(PetInfo::getDeleted, 1)
                .set(PetInfo::getUpdateTime, LocalDateTime.now())
                .eq(PetInfo::getId, petId)
                .eq(PetInfo::getDeleted, 0)
                .update();

        // 2. 清理 Redis: pet:state:{petId}
        stringRedisTemplate.delete(RedisKeyPrefix.PET_STATE_KEY + petId);

        // 3. 写状态流转日志
        long logId = snowflakeIdGenerator.nextId();
        StateTransitionLog logEntry = StateTransitionLog.builder()
                .id(logId)
                .petId(petId)
                .fromState(PetState.RUNAWAY)
                .toState(PetState.RUNAWAY)   // 保持 RUNAWAY 以标识自动解除
                .triggerType(com.heima.api.enums.TriggerType.TIMEOUT)
                .description("离家出走超过7天，自动解除领养")
                .createTime(LocalDateTime.now())
                .build();
        stateTransitionLogService.save(logEntry);

        // 4. 发布 Stream 事件
        redisStreamPublisher.publishStateChangeEvent(
                String.valueOf(logId),
                pet.getUserId(), petId, -50,
                "RUNAWAY", "ABANDONED",
                "TIMEOUT");

        log.info("自动解除领养完成: petId={}, petName={}, userId={}, runawayTime={}",
                petId, pet.getPetName(), pet.getUserId(), pet.getRunawayTime());
    }
}
