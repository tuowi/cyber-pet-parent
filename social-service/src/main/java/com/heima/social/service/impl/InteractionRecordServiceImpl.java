package com.heima.social.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heima.api.client.AuthClient;
import com.heima.api.client.PetClient;
import com.heima.api.client.ShopClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.po.UserInfo;
import com.heima.api.domain.vo.PetDetailVO;
import com.heima.api.domain.vo.PetVO;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.SocialInteractionStatus;
import com.heima.api.enums.SocialInteractionType;
import com.heima.api.enums.WSType;
import com.heima.common.util.CollUtils;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.social.domain.dto.MatchRequestDTO;
import com.heima.social.domain.dto.MatchResultDTO;
import com.heima.social.domain.dto.StealRequestDTO;
import com.heima.social.domain.dto.ws.StealAlertMessage;
import com.heima.social.domain.po.InteractionRecord;
import com.heima.social.domain.query.PageQuery;
import com.heima.social.domain.vo.MatchResultVO;
import com.heima.social.domain.vo.StealResultVO;
import com.heima.social.event.SocialEvent;
import com.heima.social.mapper.InteractionRecordMapper;
import com.heima.social.publisher.SocialEventPublisher;
import com.heima.social.service.IInteractionRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.social.utils.PetSocialMessageUtil;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 社交互动记录表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-21
 */
@Service
@RequiredArgsConstructor
public class InteractionRecordServiceImpl extends ServiceImpl<InteractionRecordMapper, InteractionRecord> implements IInteractionRecordService {

    private final StringRedisTemplate stringRedisTemplate;

    private final AuthClient authClient;

    private final PetClient petClient;

    private final RedissonClient redissonClient;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ShopClient shopClient;

    private final SocialEventPublisher socialEventPublisher;

    private final SimpMessagingTemplate messagingTemplate;   // ★ WS 推送

    /**
     * 在线心跳
     * 客户端需每 25 秒发送一次心跳。Redis Key TTL 为 30 秒，超时自动离线。
     * 每次心跳同时更新用户在 Redis Geo 中的坐标。
     *
     * @param matchRequestDTO
     * @return
     */
    @Override
    public Result<Void> heartbeat(MatchRequestDTO matchRequestDTO) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.SET social:online:{userId} 1 EX 30
        String onlineKey = RedisKeyPrefix.SOCIAL_ONLINE_KEY + userId;
        stringRedisTemplate.opsForValue().set(onlineKey, String.valueOf(1), RedisKeyPrefix.SOCIAL_ONLINE_TTL, TimeUnit.SECONDS);
        // 3.GEOADD social:geo:nearby {lng} {lat} {userId}
        stringRedisTemplate.opsForGeo().add(
                RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY,
                new Point(matchRequestDTO.getLng(), matchRequestDTO.getLat()),
                userId.toString()
        );
        // 4.返回 ok
        return Result.ok();
    }

    /**
     * 查找附近在线宠物
     * 返回结果按距离升序排列，最多 20 条。仅返回当前在线的用户。
     *
     * @param matchResultDTO
     * @return
     */
    @Override
    public Result<List<MatchResultVO>> match(MatchResultDTO matchResultDTO) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.顺便更新当前用户自己的坐标：GEOADD social:geo:nearby {lng} {lat} {userId}
        stringRedisTemplate.opsForGeo().add(
                RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY,
                new Point(matchResultDTO.getLng(), matchResultDTO.getLat()),
                userId.toString()
        );
        // 3.执行 GEORADIUS social:geo:nearby {lng} {lat} {radius} km WITHDIST ASC COUNT 20
        // redis-cli 5.0.14.1 (git:ec77f72d)
        // 3.1. 定义圆心和半径（单位：公里）
        Circle circle = new Circle(new Point(matchResultDTO.getLng(), matchResultDTO.getLat()), new Distance(matchResultDTO.getRadius(), Metrics.KILOMETERS));
        // 3.2. 设置参数：返回距离、升序排列、限制20条
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()    // 对应 WITHDIST
                .sortAscending()      // 对应 ASC
                .limit(21);           // 对应 COUNT 20，会过滤自己
        // 3.3.执行查询
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY,
                circle,
                args
        );
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        Set<Long> userIds = new HashSet<>(list.size());
        Map<Long, Distance> distanceMap = new HashMap<>(list.size());
        // 5.对每个附近 userId：批量查询 user 表获取 nickname，批量查询 pet_info 获取该用户宠物信息
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : list) {
            // 5.1.获取用户id
            String userIdStr = result.getContent().getName();
            Long userId1 = Long.valueOf(userIdStr);
            // 4.从结果中过滤掉当前用户自己
            if (userId.equals(userId1)) {
                continue;
            }
            userIds.add(userId1);
            // 5.2.获取距离
            distanceMap.put(userId1, result.getDistance());
        }
        // 批量查询用户和宠物信息时使用 IN 查询，避免 N+1 问题
        // 空结果返回空数组 []
        if (CollUtils.isEmpty(userIds)) {
            return Result.ok(Collections.emptyList());
        }
        // 6.验证每个附近用户是否仍在线：EXISTS social:online:{userId}
        // 在线状态二次确认：GEORADIUS 的结果可能包含刚超时的用户，用 EXISTS 过滤
        for (Long id : userIds) {
            // 在线状态二次确认：GEORADIUS 的结果可能包含刚超时的用户，用 EXISTS 过滤
            String onlineKey = RedisKeyPrefix.SOCIAL_ONLINE_KEY + id;
            String isOnline = stringRedisTemplate.opsForValue().get(onlineKey);
            if (isOnline == null || isOnline.isEmpty() || (!isOnline.equals("1"))) {
                userIds.remove(id);
            }
        }
        // 再次验空
        if (userIds == null || userIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserInfo> userInfos = authClient.queryUserByIds(userIds);

        // 再次验空
        if (userInfos.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 7.组装结果列表（含 distance），按距离升序返回
        List<MatchResultVO> matchResultVOList = new ArrayList<>();
        for (UserInfo userInfo : userInfos) {
            // 查询对应用户的宠物
            List<PetVO> petVOS = petClient.queryPetByUserId(userInfo.getId());
            if (CollUtils.isEmpty(petVOS)) {
                continue;
            }

            // 构建返回单体
            for (PetVO petVO : petVOS) {
                MatchResultVO vo = new MatchResultVO();
                vo.setUserId(userInfo.getId());
                vo.setNickname(userInfo.getNickname());
                BeanUtil.copyProperties(petVO, vo);
                vo.setDistance(distanceMap.get(userInfo.getId()).getValue());
                matchResultVOList.add(vo);
            }
        }
        return Result.ok(matchResultVOList);
    }

    /**
     * 偷零食
     *
     * @param stealRequestDTO
     * @return
     */
    @Transactional
    @Override
    public Result<StealResultVO> steal(StealRequestDTO stealRequestDTO) {
        // 1.校验 targetUserId 不等于当前用户，否则返回 400 "不能偷自己的宠物哦"
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        if (stealRequestDTO.getTargetUserId() == null || stealRequestDTO.getTargetPetId() == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "请求信息不全");
        }
        if (stealRequestDTO.getTargetUserId().equals(userId)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "不能偷自己的宠物哦");
        }
        // 2.校验 petId 属于当前用户：通过 Feign 调用 pet-service 或直接查询 pet_info 确认 user_id 匹配，不匹配返回 403 "这不是你的宠物哦"
        List<PetVO> petVOS = petClient.queryPetByUserId(userId);
        boolean flag = false;
        PetVO myPet = null;
        for (PetVO petVO : petVOS) {
            if (petVO.getPetId().equals(stealRequestDTO.getPetId())) {
                flag = true;
                myPet = petVO;
            }
        }
        if (!flag) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "这不是你的宠物哦");
        }

        // 3.检查目标用户是否在线：EXISTS social:online:{targetUserId}，不在线返回 400
        String onlineKey = RedisKeyPrefix.SOCIAL_ONLINE_KEY + stealRequestDTO.getTargetUserId();
        Boolean isOnline = stringRedisTemplate.hasKey(onlineKey);
        if (Boolean.FALSE.equals(isOnline)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "目标用户不在线");
        }

        // 取目标宠物名称
        PetInfo basicPetInfo = petClient.getBasicPetInfo(stealRequestDTO.getTargetPetId());

        if (basicPetInfo == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "目标宠物不存在");
        }

        // 4.获取分布式锁 social:lock:steal:{fromUserId}:{toUserId}（waitTime=3s）
        // 4.1.创建锁对象
        String stealKey = RedisKeyPrefix.SOCIAL_LOCK_STEAL_KEY + userId + stealRequestDTO.getTargetUserId();
        RLock lock = redissonClient.getLock(stealKey);
        String reward;
        try {
            // 4.2.获取锁对象
            lock.tryLock(RedisKeyPrefix.SOCIAL_LOCK_STEAL_WAIT_TIME, RedisKeyPrefix.SOCIAL_LOCK_STEAL_LEASE_TIME, TimeUnit.SECONDS);
            // 5.查询 interaction_record：WHERE pet_id=? AND to_pet_id=? AND type='STEAL_SNACK' AND create_time > today 00:00，存在则返回 400 "今天已经偷过了"
            // 获取今天的起始时间 00:00:00
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            int count = lambdaQuery()
                    .eq(InteractionRecord::getPetId, stealRequestDTO.getPetId())
                    .eq(InteractionRecord::getTargetPetId, stealRequestDTO.getTargetPetId())
                    .eq(InteractionRecord::getType, SocialInteractionType.STEAL_SNACK)
                    .gt(InteractionRecord::getCreateTime, startOfToday)
                    .count()
                    .intValue();
            if (count > 0) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "今天已经偷过了");
            }
            // 6.通过 Feign 调用 shop-service 背包接口，从目标用户背包中随机抽取一个道具（quantity > 0），扣减目标背包 1 个，增加发起方背包 1 个，返回道具名称作为 reward；若目标背包为空则返回 400 "对方背包空空如也"
            reward = shopClient.stealFromBackpack(stealRequestDTO.getTargetUserId(), userId);
            if (reward == null) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "对方背包空空如也");
            }
            // 7.通过 Feign 调用 pet-service 扣减目标宠物心情值 -5
            boolean isSuccess = petClient.deductMoodById(stealRequestDTO.getTargetPetId());
            SocialInteractionStatus status = SocialInteractionStatus.SUCCESS;
            if (!isSuccess) {
                status = SocialInteractionStatus.FAILED;
            }
            // 8.INSERT interaction_record（pet_id=发起方宠物ID, from_user_id=当前用户ID, to_user_id=targetUserId, to_pet_id=targetPetId, type=STEAL_SNACK, status=SUCCESS）
            // 8.1.生成id
            long recordId = snowflakeIdGenerator.nextId();
            LocalDateTime now = LocalDateTime.now();
            // 8.2.构建新增对象
            InteractionRecord build = InteractionRecord.builder()
                    .id(recordId)
                    .fromUserId(userId)
                    .toUserId(stealRequestDTO.getTargetUserId())
                    .petId(stealRequestDTO.getPetId())
                    .targetPetId(stealRequestDTO.getTargetPetId())
                    .type(SocialInteractionType.STEAL_SNACK)
                    .status(status)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            // 保存数据库
            save(build);
            // Publish 到 stream:social:events（触发 WS 推送通知）
            SocialEvent event = SocialEvent.builder()
                    .eventId(String.valueOf(snowflakeIdGenerator.nextId()))
                    .eventType(SocialInteractionType.STEAL_SNACK.name())
                    .fromUserId(userId)
                    .toUserId(stealRequestDTO.getTargetUserId())
                    .petId(stealRequestDTO.getPetId())
                    .targetPetId(stealRequestDTO.getTargetPetId())
                    .data(Map.of("reward", reward, "moodChange", -5))
                    .timestamp(System.currentTimeMillis())
                    .build();
            socialEventPublisher.publish(event);
            // WS 推送给被偷用户
            String nickname = authClient.queryUserById(userId).getNickname();
            StealAlertMessage alert = StealAlertMessage.builder()
                    .type(WSType.STEAL_ALERT.name())
                    .fromUserId(userId)
                    .fromNickname(nickname)
                    .petName(myPet.getPetName())
                    .reward(reward)
                    .message(nickname + "的" + myPet.getPetName()
                            + "偷了你一个" + reward + "！")
                    .timestamp(System.currentTimeMillis())
                    .build();
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(stealRequestDTO.getTargetUserId()),  // 目标用户
                    "/queue/steal",                              // 目的地
                    alert                                         // 消息体
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 10.释放锁，返回偷取结果（只有当前线程持有锁时才释放，避免 IllegalMonitorStateException）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        // 获取萌宠语录
        String cuteMessage = PetSocialMessageUtil.getRandomMessage(basicPetInfo.getSpecies(), basicPetInfo.getPersonality(), SocialInteractionType.STEAL_SNACK);

        // 构建返回结果
        StealResultVO build = StealResultVO.builder()
                .petId(stealRequestDTO.getPetId())
                .petName(myPet.getPetName())
                .targetPetId(stealRequestDTO.getTargetPetId())
                .targetPetName(basicPetInfo.getPetName())
                .reward(reward)
                .moodChange(-5)
                .cuteMessage(cuteMessage)
                .build();
        return Result.ok(build);
    }

    /**
     * 上传在线位置
     * 此接口仅更新 Geo 坐标，不刷新在线状态 TTL
     * 与 5.1 心跳接口的区别：5.1 同时更新 Geo + 续期在线 TTL，此接口仅更新 Geo
     *
     * @param matchRequestDTO
     * @return
     */
    @Override
    public Result<Void> location(MatchRequestDTO matchRequestDTO) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.GEOADD social:geo:nearby {lng} {lat} {userId}
        stringRedisTemplate.opsForGeo().add(
                RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY,
                new Point(matchRequestDTO.getLng(), matchRequestDTO.getLat()),
                userId.toString()
        );
        // 3.返回成功
        return Result.ok();
    }

    /**
     * 互动记录
     *
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<InteractionRecord>> logPageList(Integer page, Integer size) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.查询 DB：SELECT * FROM interaction_record WHERE (from_user_id=? OR to_user_id=?) ORDER BY create_time DESC LIMIT ? OFFSET ?
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 20 : size);
        Page<InteractionRecord> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        Page<InteractionRecord> result = lambdaQuery()
                .and(w -> w.eq(InteractionRecord::getFromUserId, userId)
                        .or()
                        .eq(InteractionRecord::getToUserId, userId))
                .page(sort);
        // 3.批量查询关联的 user 表（fromNickname, toNickname）和 pet_info 表（petName, toPetName）
        List<InteractionRecord> records = result.getRecords();
        for (InteractionRecord record : records) {
            String fromNickname = authClient.queryUserById(record.getFromUserId()).getNickname();
            String toNickname = authClient.queryUserById(record.getToUserId()).getNickname();

            // petName 和 toPetName 通过 pet_info 表关联查询，若宠物已被删除（deleted=1），宠物名字段返回 "已删除的宠物"
            String petName = validPetName(petClient.getBasicPetInfo(record.getPetId()));
            String toPetName = validPetName(petClient.getBasicPetInfo(record.getTargetPetId()));

            record.setFromNickname(fromNickname);
            record.setToNickname(toNickname);
            record.setPetName(petName);
            record.setTargetPetName(toPetName);

            // 4.将 type 编码映射为中文名（typeName），如 STEAL_SNACK → "偷零食"
            record.setTypeName(record.getType().getDesc());
        }
        // 5.返回 PageDTO
        PageDTO<InteractionRecord> dto = PageDTO.of(result, records);
        return Result.ok(dto);
    }

    // 校验宠物是否删除，返回正确宠物名
    private String validPetName(PetInfo petInfo) {
        if (petInfo.getDeleted() == 1) {
            return "已删除的宠物";
        }
        return petInfo.getPetName();
    }
}
