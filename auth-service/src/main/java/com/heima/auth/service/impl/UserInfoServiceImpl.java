package com.heima.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.heima.api.client.PetClient;
import com.heima.api.client.ShopClient;
import com.heima.api.client.SocialClient;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.CurrencyTransaction;
import com.heima.api.domain.po.UserWallet;
import com.heima.api.domain.vo.PetVO;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.CurrencyBizType;
import com.heima.api.enums.CurrencyTransactionType;
import com.heima.api.enums.Role;
import com.heima.auth.properties.JwtProperties;
import com.heima.auth.domain.dto.CancelAccountDTO;
import com.heima.auth.domain.dto.LoginDTO;
import com.heima.auth.domain.dto.RegisterDTO;
import com.heima.auth.domain.dto.UpdateUserInfoDTO;
import com.heima.auth.domain.po.UserInfo;
import com.heima.auth.domain.vo.CancelAccountVO;
import com.heima.auth.domain.vo.LoginVO;
import com.heima.auth.domain.vo.RegisterVO;
import com.heima.auth.domain.vo.EditAdminVO;
import com.heima.auth.domain.vo.UpdateUserInfoVO;
import com.heima.auth.mapper.UserInfoMapper;
import com.heima.auth.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.util.*;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-09
 */
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtils jwtUtils;

    private final JwtProperties jwtProperties;

    private final ShopClient shopClient;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private final SocialClient socialClient;

    private final PetClient petClient;

    private static final String CONFIRM = "我已知晓：注销后所有宠物将被释放，钱包余额清零，数据不可恢复";

    /**
     * 用户注册
     *
     * @param registerDTO
     * @return
     */
    @Transactional
    @Override
    public Result<RegisterVO> register(RegisterDTO registerDTO) {
        // 1.校验用户名和密码合法性
        // 1.1.校验用户名
        // 去掉字符串首尾两端的空白字符
        String username = registerDTO.getUsername().trim();
        // 若用户名不合法
        if (!UsernameValidatorUtil.isValid(username)) {
            return Result.fail("用户名长度不合法");
        }
        // 1.2.校验密码
        String password = registerDTO.getPassword();
        // 若密码不合法
        if (!PasswordValidatorUtil.isValid(password)) {
            return Result.fail("密码长度不合法");
        }
        // 2.查询 DB：SELECT * FROM user_info WHERE username=?，若存在则返回 409(避免重复)
        UserInfo userInfo = lambdaQuery()
                .eq(username != null, UserInfo::getUsername, username)
                .one();
        if (userInfo != null) {
            return Result.fail(BizErrorCode.CONFLICT.getCode(), BizErrorCode.CONFLICT.getMessage());
        }
        // 3.使用 Snowflake 算法生成 userId，BCrypt 加密密码（cost factor >= 10）
        // 3.1.生成用户id
        long userId = snowflakeIdGenerator.nextId();
        // 3.2.加密密码
        String encoded = passwordEncoder.encode(password);
        // 4.INSERT INTO user (id, username, password, nickname) VALUES (?, ?, ?, ?)
        // 4.1.nickname 若为空则默认取 username 的值
        String nickname = registerDTO.getNickname();
        if (nickname == null || nickname.isEmpty()) {
            nickname = username;
        }
        // 4.2.构建用户信息（角色默认 USER）
        // 统一时间
        LocalDateTime now = LocalDateTime.now();
        UserInfo userInfo1 = UserInfo.builder()
                .id(userId)
                .username(username)
                .nickname(nickname)
                .password(encoded)
                .role("USER")
                .createTime(now)
                .deleted(0)
                .build();
        save(userInfo1);
        // 5.通过 Feign 调用 shop-service 自动创建钱包：
        // 5.1.INSERT INTO user_wallet (id, user_id, balance, total_income) VALUES (?, ?, 500, 500)
        long walletId = snowflakeIdGenerator.nextId();
        UserWallet userWallet = UserWallet.builder()
                .id(walletId)
                .userId(userId)
                .balance(BigDecimal.valueOf(500))
                .totalIncome(BigDecimal.valueOf(500))
                .totalExpense(BigDecimal.valueOf(0))
                .createTime(now)
                .updateTime(now)
                .build();
        shopClient.saveUserWallet(userWallet);
        // 5.2.INSERT INTO currency_transaction (user_id, amount, type, biz_type, balance_after) VALUES (?, 500, 'INCOME', 'REGISTER_GIFT', 500)
        long currencyId = snowflakeIdGenerator.nextId();
        CurrencyTransaction currencyTransaction = CurrencyTransaction.builder()
                .id(currencyId)
                .userId(userId)
                .amount(BigDecimal.valueOf(500))
                .type(CurrencyTransactionType.INCOME)
                .bizType(CurrencyBizType.REGISTER_GIFT)
                .bizId(null)
                .balanceAfter(BigDecimal.valueOf(500))
                .createTime(now)
                .build();
        shopClient.saveCurrencyTransaction(currencyTransaction);
        // 5.3.写入 Redis：SET user:balance:{userId} 500
        String balanceKey = RedisKeyPrefix.USER_BALANCE_KEY + userId;
        stringRedisTemplate.opsForValue().set(balanceKey, String.valueOf(500));
        // TODO 注册接口应加验证码或限流（如 Redis 记录 IP 注册频率），防恶意批量注册
        // 6.返回
        RegisterVO registerVO = new RegisterVO();
        registerVO.setUserId(userId);
        registerVO.setUsername(username);
        registerVO.setNickname(nickname);
        return Result.ok(registerVO);
    }

    /**
     * 用户登录
     *
     * @param loginDTO
     * @return
     */
    @Override
    public Result<LoginVO> login(LoginDTO loginDTO) {
        // 1.校验 username 和 password 非空
        String username = loginDTO.getUsername().trim();
        String password = loginDTO.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "用户名 或者 密码 为空");
        }
        // 2.查询 DB：SELECT * FROM user WHERE username=?，不存在则返回 401（不区分用户不存在 vs 密码错误）
        UserInfo userInfo = lambdaQuery()
                .eq(true, UserInfo::getUsername, username)
                .one();
        if (userInfo == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "该用户名不存在");
        }
        // 3.BCrypt 比对密码，不匹配则返回 401
        boolean isMatches = passwordEncoder.matches(password, userInfo.getPassword());
        if (!isMatches) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "该密码不匹配");
        }
        // 4.生成 JWT Token，payload 包含 userId 和 role，expireAt 设为 2 小时后
        // 生成 JWT 时多加一个 jti，帮助之后的退出登录
        String jti = UUID.randomUUID().toString();
        String token = jwtUtils.createToken(userInfo.getId(), jwtProperties.getSecretKey(), jwtProperties.getTokenTTL(),
                Map.of(
                        "role", userInfo.getRole() != null ? userInfo.getRole() : "USER",
                        "jti", jti)
        );
        // 控制台获取jti的值
        System.out.println("jti随机数的值：" + jti);
        // 5.返回 LoginVO（token, userId, username, nickname, role, expireAt
        LoginVO loginVO = LoginVO.builder()
                .userId(userInfo.getId())
                .username(userInfo.getUsername())
                .nickname(userInfo.getNickname())
                .avatar(userInfo.getAvatar())
                .role(userInfo.getRole())
                .token(token)
                .expireAt(LocalDateTime.now().plusSeconds(jwtProperties.getTokenTTL() / 1000))
                .build();
        // TODO可考虑在 Redis 中记录登录失败次数 login:fail:{username}，超过阈值临时锁定（如 5 次/15 分钟）
        // 6.返回
        return Result.ok(loginVO);
    }

    /**
     * 根据id批量获取用户信息
     *
     * @param ids
     * @return
     */
    @Override
    public List<UserInfo> queryUserByIds(List<Long> ids) {
        // 1. 判空，避免空集合生成错误 SQL
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 拼接id
        String idStr = StrUtil.join(",", ids);
        // 批量查询
        List<UserInfo> userInfos = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();
        return userInfos;
    }

    /**
     * 退出登录
     *
     * @return
     */
    @Override
    public Result<Void> logout(String jti) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.jti 为空（老 Token）则跳过 Redis 操作，直接返回成功
        if (StrUtil.isBlank(jti)) {
            return Result.ok();
        }
        // 3.将 jti 加入用户级 Set：SADD logout:tokens:{userId} {jti}
        String logoutKey = RedisKeyPrefix.LOGOUT_TOKENS_KEY + userId;
        stringRedisTemplate.opsForSet().add(logoutKey, jti);
        // 4.设置过期：EXPIRE logout:tokens:{userId} 7200（2 小时，每次续期）
        stringRedisTemplate.expire(logoutKey, RedisKeyPrefix.LOGIN_TOKENS_TTL, TimeUnit.HOURS);
        // 5.返回成功
        return Result.ok();
    }

    /**
     * 注销用户
     *
     * @param cancelAccountDTO
     * @return
     */
    @Override
    public Result<CancelAccountVO> cancel(CancelAccountDTO cancelAccountDTO) {
        // 1. 从 UserContextHolder 获取 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2. 查询 user_info 校验账号存在且 status != 'DELETED'
        UserInfo userInfo = getById(userId);
        if (userInfo == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户已注销,不可重复注销");
        }
        // 3.校验 confirm 文案完整匹配
        if (cancelAccountDTO.getConfirm() == null || !cancelAccountDTO.getConfirm().equals(CONFIRM)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "文案错误");
        }
        // 4. BCrypt 比对 password（二次身份验证）
        boolean isMatches = passwordEncoder.matches(cancelAccountDTO.getPassword(), userInfo.getPassword());
        if (!isMatches) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "该密码不匹配");
        }
        // 5. 获取分布式锁 user:lock:deletion:{userId}
        // （leaseTime=60s，防止并发注销 + 注销中其他操作干扰）
        String deletionKey = RedisKeyPrefix.USER_LOCK_DELETION_KEY + userId;
        RLock lock = redissonClient.getLock(deletionKey);

        // 全局定义
        Integer deletedPetCount = null;
        BigDecimal forfeitedBalance = null;
        LocalDateTime now = LocalDateTime.now();
        try {
            boolean isLock = lock.tryLock(RedisKeyPrefix.USER_LOCK_DELETION_WAIT_TIME,
                    RedisKeyPrefix.USER_LOCK_DELETION_LEASE_TIME, TimeUnit.SECONDS);
            if (!isLock) {
                return Result.fail(BizErrorCode.TOO_MANY_REQUESTS.getCode(), "注销太频繁了，请稍后再试");
            }

            // 6.Step 1: social-service — 下线 + 清理社交数据
            // 6.1.DEL social:online:{userId}
            String onlineKey = RedisKeyPrefix.SOCIAL_ONLINE_KEY + userId;
            stringRedisTemplate.delete(onlineKey);
            // 6.2.ZREM social:geo:nearby {userId}
            stringRedisTemplate.opsForZSet().remove(RedisKeyPrefix.SOCIAL_GEO_NEARBY_KEY, userId.toString());
            // 6.3.查询所有 post_like WHERE user_id=? → 物理删除
            Boolean isRemove = socialClient.deleteLikeByUserId(userId);
            if (!isRemove) {
                return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "用户点赞删除失败");
            }
            // 7.Step 2: pet-service — 释放所有宠物  
            deletedPetCount = petClient.deletePetByUserId();
            // 8.Step 3: agent-service — 清除 AI 记忆(第七步已完成)
            // 8.1.DEL agent:memory:{userId}:*（pattern delete）
            String pattern = RedisKeyPrefix.AGENT_MEMORY_KEY + userId + ":*";
            clearCache(pattern);
            // 9.Step 4: shop-service — 清理钱包和背包
            // 9.1.删除钱包
            forfeitedBalance = shopClient.deleteUserWalletByUserId();
            // 9.2.删除背包
            shopClient.deleteUserBackpack();
            // 10.Step 5: analytics-service — 清理排行榜
            List<Long> petIds = petClient.queryPetByUserId(userId)
                    .stream()
                    .map(PetVO::getPetId)
                    .toList();
            // 10.1.ZREM intimacy:leaderboard {userId}:{petId}（逐只）
            for (Long id : petIds) {
                String member = userId + ":" + id;
                stringRedisTemplate.opsForZSet()
                        .remove(RedisKeyPrefix.INTIMACY_LEADERBOARD_KEY, member);
                // 10.2.DEL pet:mood:daily:{petId}:*（逐只 pattern delete）
                String moodKey = RedisKeyPrefix.PET_MOOD_DAILY_KEY + id + ":*";
                clearCache(moodKey);
            }
            // 11.Step 6: 签到数据
            // 11.1.DEL signin:{userId}:*（pattern delete）
            String signinKey = RedisKeyPrefix.SIGNIN_KEY + userId + ":*";
            stringRedisTemplate.delete(signinKey);
            // 12.Step 7: auth-service — 标记用户注销
            // 12.1.UPDATE user_info SET status='DELETED',deleted_at=NOW() WHERE id=? AND status='ACTIVE'
            removeById(userId);
            // 12.2.撤销所有 Token：DEL logout:tokens:{userId} + SET user:token:revoked:{userId} {now}
            stringRedisTemplate.delete(RedisKeyPrefix.LOGOUT_TOKENS_KEY + userId);
            stringRedisTemplate.opsForValue().set(RedisKeyPrefix.USER_TOKEN_REVOKED_KEY + userId, now.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            // 12.3. 释放分布式锁 user:lock:deletion:{userId}
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // TODO 13.断开该用户的 WebSocket 连接（如有）

        // 14.返回
        CancelAccountVO cancelAccountVO = new CancelAccountVO();
        cancelAccountVO.setDeletedPetCount(deletedPetCount);
        cancelAccountVO.setForfeitedBalance(forfeitedBalance);
        cancelAccountVO.setDeletedAt(now);
        return Result.ok(cancelAccountVO);
    }

    // 清理缓存
    void clearCache(String pattern) {
        var keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /**
     * 设置管理员
     *
     * @param userId
     * @return
     */
    @Override
    public Result<EditAdminVO> admin(Long userId) {
        // 1. 从 UserContextHolder 获取 userId
        Long userIdNow = UserContextHolder.getUserId();
        if (userIdNow == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.查询当前用户的 role：SELECT role FROM user_info WHERE id=? AND status='ACTIVE'，若 role != 'ADMIN' 则返回 403
        if (!UserContextHolder.isAdmin()) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "无管理员权限");
        }
        // 3.校验 targetUserId != 当前 userId，否则返回 400 "不能设置自己的角色"
        if (userIdNow.equals(userId)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "不能设置自己的角色");
        }
        // 4.查询目标用户：SELECT * FROM user_info WHERE id=? AND status='ACTIVE'，不存在返回 404；若 role 已是 'ADMIN' 返回 400 "该用户已是管理员"
        UserInfo targetUserInfo = getById(userId);
        if (targetUserInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该用户不存在");
        }
        if (targetUserInfo.getRole().equals(Role.ADMIN.name())) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "该用户已是管理员");
        }
        // 5.UPDATE user_info SET role='ADMIN' WHERE id=? AND role='USER' AND status='ACTIVE'
        lambdaUpdate()
                .set(UserInfo::getRole, Role.ADMIN.name())
                .eq(UserInfo::getId, userId)
                .eq(UserInfo::getRole, Role.USER.name())
                .update();
        // 6.返回目标用户信息（userId, username, nickname, role）
        EditAdminVO setAdminVO = new EditAdminVO();
        setAdminVO.setUserId(userId);
        setAdminVO.setUsername(targetUserInfo.getUsername());
        setAdminVO.setNickname(targetUserInfo.getNickname());
        setAdminVO.setRole(Role.ADMIN);
        return Result.ok(setAdminVO);
    }

    /**
     * 编辑用户信息（昵称）
     *
     * @param updateUserInfoDTO
     * @return
     */
    @Override
    public Result<UpdateUserInfoVO> updateUserInfo(UpdateUserInfoDTO updateUserInfoDTO) {
        // 1. 获取当前用户
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2. 校验昵称
        String nickname = updateUserInfoDTO.getNickname();
        if (StrUtil.isBlank(nickname)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "昵称不能为空");
        }
        nickname = nickname.trim();
        if (nickname.length() < 1 || nickname.length() > 20) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "昵称长度1-20位");
        }
        // 3. 更新数据库
        lambdaUpdate()
                .set(UserInfo::getNickname, nickname)
                .eq(UserInfo::getId, userId)
                .update();
        // 4. 查询并返回更新后的用户信息
        UserInfo userInfo = getById(userId);
        UpdateUserInfoVO vo = new UpdateUserInfoVO();
        vo.setUserId(userInfo.getId());
        vo.setUsername(userInfo.getUsername());
        vo.setNickname(userInfo.getNickname());
        vo.setAvatar(userInfo.getAvatar());
        return Result.ok(vo);
    }

    /**
     * 取消管理员
     *
     * @param userId
     * @return
     */
    @Override
    public Result<EditAdminVO> demoteAdmin(Long userId) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userIdNow = UserContextHolder.getUserId();
        if (userIdNow == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.查询当前用户的 role：SELECT role FROM user_info WHERE id=? AND status='ACTIVE'，若 role != 'ADMIN' 则返回 403
        if (!UserContextHolder.isAdmin()) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "仅管理员可执行此操作");
        }
        // 3.校验 targetUserId != 当前 userId，否则返回 400 "不能取消自己的管理员权限"
        if (userIdNow.equals(userId)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "不能取消自己的管理员权限");
        }
        // 4.查询目标用户：SELECT * FROM user_info WHERE id=? AND status='ACTIVE'，不存在返回 404；若 role 不是 'ADMIN' 返回 400 "该用户不是管理员"
        UserInfo targetUserInfo = getById(userId);
        if (targetUserInfo == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该用户不存在");
        }
        if (!targetUserInfo.getRole().equals(Role.ADMIN.name())) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "该用户不是管理员");
        }
        // 5.统计当前管理员总数：SELECT COUNT(*) FROM user_info WHERE role='ADMIN' AND status='ACTIVE'，若 <= 1 则返回 400 "不能取消最后一个管理员"
        int countAdmin = lambdaQuery()
                .eq(UserInfo::getRole, Role.ADMIN)
                .count()
                .intValue();
        if (countAdmin <= 1) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "不能取消最后一个管理员");
        }
        // 6.UPDATE user_info SET role='USER' WHERE id=? AND role='ADMIN' AND status='ACTIVE'
        lambdaUpdate()
                .set(UserInfo::getRole, Role.USER.name())
                .eq(UserInfo::getId, userId)
                .eq(UserInfo::getRole, Role.ADMIN.name())
                .update();
        // 7.返回目标用户信息（userId, username, nickname, role='USER'）
        EditAdminVO editAdminVO = new EditAdminVO();
        editAdminVO.setUserId(userId);
        editAdminVO.setUsername(targetUserInfo.getUsername());
        editAdminVO.setNickname(targetUserInfo.getNickname());
        editAdminVO.setRole(Role.USER);
        return Result.ok(editAdminVO);
    }
}
