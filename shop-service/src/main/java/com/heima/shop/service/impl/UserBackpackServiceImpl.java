package com.heima.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.ProductEffectDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.BizErrorCode;
import com.heima.common.util.CollUtils;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.shop.domain.po.Product;
import com.heima.shop.domain.po.UserBackpack;
import com.heima.shop.domain.query.PageQuery;
import com.heima.shop.domain.vo.UserBackpackVO;
import com.heima.shop.mapper.UserBackpackMapper;
import com.heima.shop.service.IProductService;
import com.heima.shop.service.IUserBackpackService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户背包表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-22
 */
@Service
@RequiredArgsConstructor
public class UserBackpackServiceImpl extends ServiceImpl<UserBackpackMapper, UserBackpack> implements IUserBackpackService {

    private final IProductService productService;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 从目标背包随机偷一个道具，返回道具名称
     * 从目标用户背包中随机抽取一个道具（quantity > 0），扣减目标背包 1 个，增加发起方背包 1 个，
     * 返回道具名称作为 reward；若目标背包为空则返回 400 "对方背包空空如也"
     *
     * @param targetUserId
     * @param thiefUserId
     * @return
     */
    @Transactional
    @Override
    public String steal(Long targetUserId, Long thiefUserId) {
        // 1.获取目标用户背包
        List<UserBackpack> list = lambdaQuery()
                .eq(UserBackpack::getUserId, targetUserId)
                .gt(UserBackpack::getQuantity, 0)
                .list();
        // 2.检验是否为空
        if (CollUtils.isEmpty(list)) {
            return null;
        }
        // 3.随机获取道具
        Random rand = new Random();
        UserBackpack userBackpack = list.get(rand.nextInt(list.size()));
        // 4.扣减目标背包 1 个
        lambdaUpdate()
                .set(UserBackpack::getQuantity, userBackpack.getQuantity() - 1)
                .set(UserBackpack::getTotalConsumed, userBackpack.getTotalConsumed() + 1)
                .set(UserBackpack::getUpdateTime, LocalDateTime.now())
                .eq(UserBackpack::getUserId, targetUserId)
                .update();
        // 5.增加发起方背包 1 个
        // 5.1.检验发起方是否包含这种道具
        UserBackpack one = lambdaQuery()
                .eq(UserBackpack::getProductId, userBackpack.getProductId())
                .eq(UserBackpack::getUserId, thiefUserId)
                .one();
        if (one == null) {
            // 新增
            long packId = snowflakeIdGenerator.nextId();
            UserBackpack build = UserBackpack.builder()
                    .id(packId)
                    .userId(thiefUserId)
                    .productId(userBackpack.getProductId())
                    .quantity(1)
                    .totalAcquired(1)
                    .totalConsumed(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            save(build);
        } else {
            // 更新
            one.setQuantity(one.getQuantity() + 1);
            one.setTotalAcquired(one.getTotalAcquired() + 1);
            one.setUpdateTime(LocalDateTime.now());

            updateById(one);
        }
        // 6.查询道具名称并返回
        return productService.getById(userBackpack.getProductId()).getName();
    }

    /**
     * 消耗背包道具（互动时调用），扣减 1 个，返回道具效果值。
     */
    @Transactional
    @Override
    public ProductEffectDTO consumeItem(Long userId, Long productId) {
        // 1. 查背包
        UserBackpack backpack = lambdaQuery()
                .eq(UserBackpack::getUserId, userId)
                .eq(UserBackpack::getProductId, productId)
                .gt(UserBackpack::getQuantity, 0)
                .one();
        if (backpack == null) {
            return null;  // 背包没有该道具
        }

        // 2. 查商品效果
        Product product = productService.getById(productId);
        if (product == null || product.getDeleted() == 1) {
            return null;
        }

        // 3. 扣减背包
        lambdaUpdate()
                .set(UserBackpack::getQuantity, backpack.getQuantity() - 1)
                .set(UserBackpack::getTotalConsumed, backpack.getTotalConsumed() + 1)
                .set(UserBackpack::getUpdateTime, LocalDateTime.now())
                .eq(UserBackpack::getUserId, userId)
                .eq(UserBackpack::getProductId, productId)
                .update();

        // 4. 返回效果值
        return ProductEffectDTO.builder()
                .effectType(product.getEffectType())
                .effectHunger(product.getEffectHunger() != null ? product.getEffectHunger() : 0)
                .effectMood(product.getEffectMood() != null ? product.getEffectMood() : 0)
                .effectCleanliness(product.getEffectCleanliness() != null ? product.getEffectCleanliness() : 0)
                .productName(product.getName())
                .build();
    }

    /**
     * 我的背包
     * 仅返回 quantity > 0 的道具，已用完的不显示。data 为空背包时返回 records: [], total: 0。
     *
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<UserBackpackVO>> pageList(Integer page, Integer size) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "用户未登录");
        }
        // 2.分页查询：SELECT bp.product_id, bp.quantity, bp.first_acquired, bp.last_used, p.name, p.icon, p.description, p.effect_type FROM user_backpack bp INNER JOIN product p ON bp.product_id = p.id WHERE bp.user_id = ? AND bp.quantity > 0 ORDER BY bp.first_acquired DESC LIMIT ? OFFSET ?
        // 2.1.设置分页参数
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(page == null ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);
        Page<UserBackpack> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        // 2.2.查询用户背包
        lambdaQuery()
                .eq(UserBackpack::getUserId, userId)
                .gt(UserBackpack::getQuantity, 0)
                .page(sort);
        // 3.查询商品数据，并且类型转换
        // 3.1.提取商品id
        List<UserBackpack> records = sort.getRecords();

        // 若数据为空
        if (records == null || records.isEmpty()) {
            PageDTO<UserBackpackVO> empty = PageDTO.empty(sort);
            return Result.ok(empty);
        }
        List<Long> ids = records
                .stream()
                .map(UserBackpack::getProductId)
                .toList();

        Map<Long, UserBackpack> userBackpackMap = records.stream()
                .collect(Collectors.toMap(UserBackpack::getProductId, Function.identity()));

        // 3.2.进行商品查询
        List<Product> products = productService.listByIds(ids);

        // 转成 Map<id, Product>
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 3.3.构建返回数据
        List<UserBackpackVO> userBackpackVOS = new ArrayList<>(ids.size());
        for (Long id : ids) {
            UserBackpackVO userBackpackVO = new UserBackpackVO();
            userBackpackVO.setProductId(id);

            Product p = productMap.get(id);
            userBackpackVO.setName(p.getName());
            userBackpackVO.setIcon(p.getIcon());
            userBackpackVO.setDescription(p.getDescription());
            userBackpackVO.setEffectType(p.getEffectType());
            userBackpackVO.setEffectTypeName(p.getEffectType().getDesc());

            UserBackpack bp = userBackpackMap.get(id);
            userBackpackVO.setQuantity(bp.getQuantity());
            userBackpackVO.setFirstAcquired(bp.getCreateTime());

            userBackpackVOS.add(userBackpackVO);
        }

        // 4.返回 PageDTO
        PageDTO<UserBackpackVO> dto = PageDTO.of(sort, userBackpackVOS);
        return Result.ok(dto);
    }

    /**
     * 内部接口-用户注销删除背包
     */
    @Override
    public void deleteUserBackpack() {
        // 1.DELETE FROM user_backpack WHERE user_id=?
        Long userId = UserContextHolder.getUserId();
        LambdaQueryWrapper<UserBackpack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBackpack::getUserId, userId);
        remove(wrapper);
        // 2.DEL shop:limit:{userId}:*:*（pattern delete）
        String pattern = RedisKeyPrefix.SHOP_LIMIT_KEY + userId + ":*:*";
        clearDailyLimitCache(pattern);
    }

    //  删除限购缓存
    private void clearDailyLimitCache(String pattern) {
        var keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
