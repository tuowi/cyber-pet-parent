package com.heima.pet.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.*;
import com.heima.api.utils.PersonalityRandomUtil;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.pet.domain.po.PetAttribute;
import com.heima.pet.domain.po.PetInfo;
import com.heima.pet.domain.po.StateTransitionLog;
import com.heima.pet.domain.redis.PetStateRedis;
import com.heima.pet.domain.vo.PetAttributeVO;
import com.heima.pet.domain.vo.PetDetailVO;
import com.heima.pet.domain.vo.PetRecoverVO;
import com.heima.pet.domain.vo.PetVO;
import com.heima.pet.mapper.PetInfoMapper;
import com.heima.pet.service.IPetAttributeService;
import com.heima.pet.service.IPetInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.pet.service.IStateTransitionLogService;
import com.heima.pet.service.PetStateEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 宠物基础信息表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Service
@RequiredArgsConstructor
public class PetInfoServiceImpl extends ServiceImpl<PetInfoMapper, PetInfo> implements IPetInfoService {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final IPetAttributeService petAttributeService;

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Lazy
    private IStateTransitionLogService stateTransitionLogService;

    /**
     * 领养宠物
     *
     * @param petName
     * @param species
     * @return
     */
    @Transactional
    @Override
    public Result<PetVO> foster(String petName, Species species) {
        // 1.校验 petName（1-20 字符，trim 后非空）和 species（必须是 CAT 或 DOG）
        String trimName = petName.trim();
        int petNameLength = trimName.length();
        if (petNameLength < 1 || petNameLength > 20) {
            return Result.fail("宠物名长度不合法");
        }
        if (species == null) {
            return Result.fail("物种输入错误");
        }
        // 2.统计当前用户宠物数：SELECT COUNT(*) FROM pet_info WHERE user_id=? AND deleted=0，超过 5 返回 400
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail("请重新登录");
        }
        int count = lambdaQuery()
                .eq(userId != null, PetInfo::getUserId, userId)
                .eq(PetInfo::getDeleted, 0)
                .count()
                .intValue();
        if (count > 5) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "已达领养上限（5只）");
        }
        // 3.生成 Snowflake ID，按权重随机性格（粘人 40%、傲娇 25%、贪吃 25%、佛系 10%）
        // 3.1.生成 Snowflake ID
        long petId = snowflakeIdGenerator.nextId();
        // 3.2.按照权重随机生成性格
        String personality = PersonalityRandomUtil.randomByWeight().getName();
        // 4.@Transactional：INSERT pet_info + INSERT pet_attribute（hunger=100, mood=100, cleanliness=100, state=NORMAL）
        // 4.1.添加宠物信息
        LocalDateTime now = LocalDateTime.now();
        PetInfo petInfo = PetInfo.builder()
                .id(petId)
                .userId(userId)
                .petName(trimName)
                .species(species)
                .personality(personality)
                .state(PetState.NORMAL)
                .runawayTime(null)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        // 4.2.添加宠物属性
        long petAttributeId = snowflakeIdGenerator.nextId();
        PetAttribute petAttribute = PetAttribute.builder()
                .id(petAttributeId)
                .petId(petId)
                .hunger(100)
                .mood(100)
                .cleanliness(100)
                .lastDecayTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        insertPet(petInfo, petAttribute);

        // 5.写入 Redis Hash pet:state:{petId} 初始化属性（hunger, mood, cleanliness, state）
        String key = RedisKeyPrefix.PET_STATE_KEY + petId;
        // 5.1. 构建 Map（所有值转为 String，因为用的是 StringRedisTemplate）
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("hunger", String.valueOf(100));
        initialState.put("mood", String.valueOf(100));
        initialState.put("cleanliness", String.valueOf(100));
        initialState.put("state", PetState.NORMAL.name());
        initialState.put("stateSince", String.valueOf(System.currentTimeMillis()));
        // 5.2.存入redis
        stringRedisTemplate.opsForHash().putAll(key, initialState);
        // 6.返回 PetFosterVO
        PetAttributeVO petAttributeVO = new PetAttributeVO();
        BeanUtil.copyProperties(petAttribute, petAttributeVO);
        PetVO petFosterVO = PetVO.builder()
                .petId(petId)
                .petName(trimName)
                .species(species)
                .speciesName(species.getDesc())
                .personality(personality)
                .state(PetState.NORMAL)
                .stateName(PetState.NORMAL.getDesc())
                .runawayTime(null)
                .createTime(now)
                .attributes(petAttributeVO)
                .build();
        return Result.ok(petFosterVO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void insertPet(PetInfo petInfo, PetAttribute petAttribute) {
        save(petInfo);
        petAttributeService.save(petAttribute);
    }

    /**
     * 获取宠物详情
     *
     * @param petId
     * @return
     */
    @Override
    public Result<PetDetailVO> getPetDetail(Long petId) {
        // 1.查询 pet_info by id，若为 null 或 deleted=1 则返回 404
        // 测试deleted是否自动添加deleted=0(已测试加入)
        PetInfo petInfo = getById(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.NOT_FOUND.getMessage());
        }
        // 2.比对 pet_info.user_id 与 UserContextHolder 中的当前 userId，不匹配则返回 403
        Long userId = UserContextHolder.getUserId();
        if (!petInfo.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 3.读取 Redis Hash pet:state:{petId} 获取实时属性（hunger, mood, cleanliness, state）
        String key = RedisKeyPrefix.PET_STATE_KEY + petId;
        PetAttribute petAttribute = null;
        PetStateRedis petStateRedis = new PetStateRedis();
        Map<Object, Object> petStateMap = stringRedisTemplate.opsForHash()
                .entries(key);
        // TODO 局部回滚：Redis 未命中可能发生在服务重启后 — 必须回退到 DB 查询，不能信任 Redis 一定有数据
        if (petStateMap.isEmpty()) {
            // 4.Redis 未命中时：回退查询 DB pet_attribute 表，并将结果回写到 Redis Hash
            // 4.1.查询数据库
            petAttribute = petAttributeService.lambdaQuery()
                    .eq(petId != null, PetAttribute::getPetId, petId)
                    .one();
            // 4.2.结果写到redis
            Map<String, Object> petState = new HashMap<>();
            petState.put("hunger", String.valueOf(petAttribute.getHunger()));
            petState.put("mood", String.valueOf(petAttribute.getMood()));
            petState.put("cleanliness", String.valueOf(petAttribute.getCleanliness()));
            petState.put("state", petInfo.getState().name());
            stringRedisTemplate.opsForHash().putAll(key, petState);
            // 类型转换
            BeanUtil.copyProperties(petAttribute, petStateRedis);
            petStateRedis.setState(petInfo.getState());
        } else {
            // 非空，转换类型
            petStateRedis = BeanUtil.fillBeanWithMap(petStateMap, new PetStateRedis(), false);
        }
        // 5.合并 pet_info（静态信息）+ Redis/DB（实时属性），返回 PetDetailVO
        PetAttributeVO petAttributeVO = new PetAttributeVO();
        BeanUtil.copyProperties(petStateRedis, petAttributeVO);
        PetDetailVO petDetailVO = PetDetailVO.builder()
                .petId(petId)
                .petName(petInfo.getPetName())
                .species(petInfo.getSpecies())
                .speciesName(petInfo.getSpecies().getDesc())
                .personality(petInfo.getPersonality())
                .state(petInfo.getState())
                .stateName(petInfo.getState().getDesc())
                .avatar(petInfo.getAvatar())
                .runawayTime(petInfo.getRunawayTime())
                .createTime(petInfo.getCreateTime())
                .attributes(petAttributeVO)
                .build();
        return Result.ok(petDetailVO);
    }

    /**
     * 获取我的宠物列表
     *
     * @return
     */
    @Override
    public Result<List<PetVO>> getPetList() {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.查询 DB：SELECT * FROM pet_info WHERE user_id=? AND deleted=0 ORDER BY create_time DESC
        // 2.1.查询数据库
        List<PetInfo> petInfos = lambdaQuery()
                .eq(true, PetInfo::getUserId, userId)
                .orderByDesc(PetInfo::getCreateTime)
                .list();
        // 2.2.若无宠物直接返回
        if (CollUtil.isEmpty(petInfos)) {
            return Result.ok(Collections.emptyList());
        }
        // 3.遍历每只宠物，批量读取 Redis Hash pet:state:{petId}（使用 pipeline 减少 RTT）
        List<Object> list = stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    // 注意：这里要用原生连接，或者用 stringRedisTemplate 的 ops
                    // 为了简便，用 lambda 包裹 stringRedisTemplate 操作
                    for (PetInfo pet : petInfos) {
                        String key = RedisKeyPrefix.PET_STATE_KEY + pet.getId();
                        // 连续发送 HGETALL 命令，但不等返回
                        connection.hashCommands().hGetAll(key.getBytes());
                    }
                    return null;
                }
        );
        // 4.对于 Redis 未命中的宠物，回退查询 DB pet_attribute 并回写 Redis
// 防御性检查：确保返回结果的数量与请求数量一致
        if (CollUtil.isEmpty(list)) {
            // 可以降级为全部查 DB，或者抛出异常
            // 这里我们选择全部当作未命中
            list = new ArrayList<>(Collections.nCopies(petInfos.size(), null));
        }
        // 4.1.记录待查询的宠物id
        List<Map<Long, PetStateRedis>> petStateRedisList = new ArrayList<>();
        List<Long> missPetIds = new ArrayList<>();
        for (int i = 0; i < petInfos.size(); i++) {
            Long petId = petInfos.get(i).getId();
            Map<String, String> map = (Map<String, String>) list.get(i);

            if (map != null && !map.isEmpty()) {
                // redis命中，直接转换
                PetStateRedis petStateRedis = BeanUtil.fillBeanWithMap(map, new PetStateRedis(), false);
                Map<Long, PetStateRedis> hash = new HashMap<>();
                hash.put(petId, petStateRedis);
                petStateRedisList.add(hash);
            } else {
                //未命中,记录待查询的id
                missPetIds.add(petId);
            }
        }
        // 4.2.查询数据库
        List<PetStateRedis> missPetStateList = new ArrayList<>();
        if (CollUtil.isNotEmpty(missPetIds)) {
            // 4.2.1.查询数据库
            missPetStateList = missPetIds.stream()
                    .map(missPetId -> {
                        PetAttribute petAttribute = petAttributeService.lambdaQuery()
                                .eq(missPetId != null, PetAttribute::getPetId, missPetId)
                                .one();

                        // 4.2.2.类型转换
                        PetStateRedis petStateRedis = new PetStateRedis();
                        for (PetInfo petInfo : petInfos) {
                            if (petInfo.getId().equals(missPetId)) {
                                // 4.2.3.查询状态
                                petStateRedis.setState(petInfo.getState());
                                BeanUtil.copyProperties(petAttribute, petStateRedis);

                                // 4.3.写回redis
                                String key = RedisKeyPrefix.PET_STATE_KEY + missPetId;
                                Map<String, Object> map = BeanUtil.beanToMap(petStateRedis, new HashMap<>(),
                                        CopyOptions.create().setIgnoreNullValue(true)
                                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                                stringRedisTemplate.opsForHash().putAll(key, map);
                            }
                        }

                        // 合并
                        Map<Long, PetStateRedis> missMap = new HashMap<>();
                        missMap.put(missPetId, petStateRedis);
                        petStateRedisList.add(missMap);

                        return petStateRedis;
                    })
                    .toList();
        }
        // 5.组装 PetVO 列表并返回（无宠物时返回空数组 []）
        List<PetVO> petVOS = new ArrayList<>();
        for (PetInfo petInfo : petInfos) {
            PetVO petVO = BeanUtil.copyProperties(petInfo, PetVO.class);
            petVO.setPetId(petInfo.getId());
            petVO.setSpeciesName(petInfo.getSpecies().getDesc());
            petVO.setStateName(petInfo.getState().getDesc());
            for (Map<Long, PetStateRedis> psMap : petStateRedisList) {
                for (Long id : psMap.keySet()) {
                    if (id.equals(petInfo.getId())) {
                        PetStateRedis p1 = psMap.get(id);
                        petVO.setAttributes(BeanUtil.copyProperties(p1, PetAttributeVO.class));
                    }
                }
            }
            petVOS.add(petVO);
        }
        return Result.ok(petVOS);
    }

    /**
     * 寻找离家出走的宠物
     *
     * @param petId
     * @return
     */
    @Override
    public Result<PetRecoverVO> recover(Long petId) {
        // 校验是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 校验宠物id是否为空
        if (petId == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1.查询 pet_info by id，若为 null 或 deleted=1 则返回 404
        PetInfo petInfo = getById(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.校验 userId 与当前用户一致，否则返回 403
        if (!userId.equals(petInfo.getUserId())) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 3.检查 pet_info.state 是否为 RUNAWAY，不是则返回 400 "该宠物没有离家出走"
        if (!petInfo.getState().equals(PetState.RUNAWAY)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), BizErrorCode.BAD_REQUEST.getMessage());
        }
        // 4.检查是否在 7 天找回窗口内：NOW() - pet_info.runaway_time < 7 days，超时则触发自动解除领养并返回 400 "已超过7天"
        boolean lessThan7Day = petInfo.getRunawayTime().isAfter(LocalDateTime.now().minusDays(7));
        if (!lessThan7Day) {
            // 4.1.超时则触发自动解除领养
            cancelPet(petId);
            // 4.2.返回
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "已超过7天");
        }
        // 5.@Transactional：UPDATE pet_attribute SET hunger=30, mood=30, cleanliness=30；UPDATE pet_info SET state='NORMAL', runaway_time=NULL
        resetPetState(petId);
        // 6.写入 Redis Hash pet:state:{petId} 更新属性（hunger=30, mood=30, cleanliness=30, state=NORMAL）
        String key = RedisKeyPrefix.PET_STATE_KEY + petId;
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("hunger", String.valueOf(PetStateEngine.RUNAWAY_RECOVER));
        stateMap.put("mood", String.valueOf(PetStateEngine.RUNAWAY_RECOVER));
        stateMap.put("cleanliness", String.valueOf(PetStateEngine.RUNAWAY_RECOVER));
        stateMap.put("state", PetState.NORMAL.name());
        stringRedisTemplate.opsForHash().putAll(key, stateMap);
        // 7.INSERT state_transition_log（RUNAWAY → NORMAL, trigger=MANUAL）
        StateTransitionLog stateTransitionLog = StateTransitionLog.builder()
                .fromState(PetState.RUNAWAY)
                .toState(PetState.NORMAL)
                .triggerType(TriggerType.MANUAL)
                .description("宠物已找回")
                .petId(petId)
                .createTime(LocalDateTime.now())
                .build();
        stateTransitionLogService.save(stateTransitionLog);
        // 8.返回
        PetAttributeVO petAttributeVO = new PetAttributeVO();
        petAttributeVO.setHunger(PetStateEngine.RUNAWAY_RECOVER);
        petAttributeVO.setMood(PetStateEngine.RUNAWAY_RECOVER);
        petAttributeVO.setCleanliness(PetStateEngine.RUNAWAY_RECOVER);

        PetRecoverVO recoverVO = PetRecoverVO.builder()
                .petId(petId)
                .petName(petInfo.getPetName())
                .state(PetState.NORMAL)
                .stateName(PetState.NORMAL.getDesc())
                .runawayTime(null)
                .attributes(petAttributeVO)
                .build();
        return Result.ok(recoverVO);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void resetPetState(Long petId) {
        // @Transactional：UPDATE pet_attribute SET hunger=30, mood=30, cleanliness=30；UPDATE pet_info SET state='NORMAL', runaway_time=NULL
        // 1.更新pet_attribute
        petAttributeService.lambdaUpdate()
                .set(PetAttribute::getHunger, PetStateEngine.RUNAWAY_RECOVER)
                .set(PetAttribute::getMood, PetStateEngine.RUNAWAY_RECOVER)
                .set(PetAttribute::getCleanliness, PetStateEngine.RUNAWAY_RECOVER)
                .set(PetAttribute::getUpdateTime, LocalDateTime.now())
                .eq(PetAttribute::getPetId, petId)
                .update();
        // 2.更新pet_info
        updateById(PetInfo.builder()
                .state(PetState.NORMAL)
                .id(petId)
                .build());
        LambdaUpdateWrapper<PetInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(PetInfo::getRunawayTime, null)
                .set(PetInfo::getUpdateTime, LocalDateTime.now())
                .eq(PetInfo::getId, petId);
        update(null, wrapper); // 第一个参数为 null
    }

    /**
     * 解除领养
     *
     * @param petId
     * @return
     */
    @Override
    public Result<Void> deletePet(Long petId) {
        // 校验是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 校验宠物id是否为空
        if (petId == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1.查询 pet_info by id，若为 null 或 deleted=1 则返回 404
        PetInfo petInfo = getById(petId);
        if (petInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.校验 userId 与当前用户一致，否则返回 403
        if (!userId.equals(petInfo.getUserId())) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        cancelPet(petId);
        // 6.返回
        return Result.ok();
    }

    private void cancelPet(Long petId) {
        // 3.软删除：UPDATE pet_info SET deleted=1, delete_time=NOW() WHERE id=? AND deleted=0
        lambdaUpdate()
                .set(PetInfo::getDeleted, 1)
                .set(PetInfo::getUpdateTime, LocalDateTime.now())
                .eq(PetInfo::getId, petId)
                .eq(PetInfo::getDeleted, 0)
                .update();
        // 4.清理 Redis：DEL pet:state:{petId}，DEL agent:memory:{userId}:{petId}
        // 4.1.清理 Redis：DEL pet:state:{petId}
        String key = RedisKeyPrefix.PET_STATE_KEY + petId;
        stringRedisTemplate.delete(key);
        // 4.2.清理DEL agent:memory:{userId}:{petId}
        Long userId = UserContextHolder.getUserId();
        String memoryKey = RedisKeyPrefix.AGENT_MEMORY_KEY + userId + ":" + petId;
        stringRedisTemplate.delete(memoryKey);
        // 5.从亲密度排行榜移除：ZREM intimacy:leaderboard {userId}:{petId}
        String member = userId + ":" + petId;
        stringRedisTemplate.opsForZSet().remove(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, member);
    }

    /**
     * 根据用户id列表批量查询宠物信息
     *
     * @param userIds
     * @return
     */
    @Override
    public List<PetVO> queryPetByUserIds(List<Long> userIds) {
        String userIdsStr = StrUtil.join(",", userIds);
        List<PetInfo> petInfos = query()
                .in("userId", userIdsStr)
                .last("ORDER BY FIELD(id" + userIdsStr + ")")
                .list();

        List<PetVO> petVOS = new ArrayList<>(petInfos.size());
        for (PetInfo petInfo : petInfos) {
            PetVO petVO = BeanUtil.copyProperties(petInfo, PetVO.class);
            petVO.setPetId(petInfo.getId());
            petVO.setSpeciesName(petInfo.getSpecies().getDesc());
            petVO.setStateName(petInfo.getState().getDesc());
            petVO.setAttributes(null);
            petVOS.add(petVO);
        }

        return petVOS;
    }

    /**
     * 根据用户id批量查询宠物信息
     *
     * @param userId
     * @return
     */
    @Override
    public List<PetVO> queryPetByUserId(Long userId) {
        List<PetInfo> petInfos = lambdaQuery()
                .eq(PetInfo::getUserId, userId)
                .list();

        List<PetVO> petVOS = new ArrayList<>(petInfos.size());
        for (PetInfo petInfo : petInfos) {
            PetVO petVO = BeanUtil.copyProperties(petInfo, PetVO.class);
            petVO.setPetId(petInfo.getId());
            petVO.setSpeciesName(petInfo.getSpecies().getDesc());
            petVO.setStateName(petInfo.getState().getDesc());
            petVO.setAttributes(null);
            petVOS.add(petVO);
        }

        return petVOS;
    }

    /**
     * 内部接口-根据id按照顺序批量返回信息
     *
     * @param petIds
     * @return
     */
    @Override
    public List<PetInfo> queryPetByIds(List<Long> petIds) {
        // 1.判空
        if (petIds == null || petIds.isEmpty()) {
            return List.of();
        }
        // 2.拼接id
        String petIdStr = StrUtil.join(",", petIds);
        // 3.批量查询
        List<PetInfo> petInfos = query()
                .in("id", petIds)
                .last("ORDER BY FIELD(id," + petIdStr + ")")
                .list();
        return petInfos;
    }

    /**
     * 内部接口-用户注销删除宠物
     *
     * @return
     */
    @Override
    public Integer deletePetByUserId() {
        // 1.查询用户所有宠物（deleted=0）
        Long userId = UserContextHolder.getUserId();
        LambdaQueryWrapper<PetInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PetInfo::getUserId, userId);
        List<PetInfo> petInfos = list(wrapper);
        if (petInfos.isEmpty()){
            return 0;
        }
        // 2.逐只执行 3.6 解除领养逻辑
        for (PetInfo p : petInfos) {
            deletePet(p.getId());
        }
        return petInfos.size();
    }
}
