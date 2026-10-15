package com.heima.shop.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.CurrencyBizType;
import com.heima.api.enums.CurrencyTransactionType;
import com.heima.api.enums.OrderStatus;
import com.heima.api.enums.ProductStatus;
import com.heima.common.util.CollUtils;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.shop.domain.po.*;
import com.heima.shop.domain.query.OrderPlaceQuery;
import com.heima.shop.domain.query.PageQuery;
import com.heima.shop.domain.vo.OrderListVO;
import com.heima.shop.domain.vo.OrderVO;
import com.heima.shop.mapper.ShopOrderMapper;
import com.heima.shop.service.IProductService;
import com.heima.shop.service.ICurrencyTransactionService;
import com.heima.shop.service.IShopOrderService;
import com.heima.shop.service.IUserBackpackService;
import com.heima.shop.service.IUserWalletService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
@Service
@RequiredArgsConstructor
public class ShopOrderServiceImpl extends ServiceImpl<ShopOrderMapper, ShopOrder> implements IShopOrderService {

    @Autowired
    @Lazy
    private IProductService productService;

    private final StringRedisTemplate stringRedisTemplate;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    @Lazy
    private IUserBackpackService userBackpackService;

    private final IUserWalletService userWalletService;

    private final ICurrencyTransactionService currencyTransactionService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final static DefaultRedisScript<Long> DEDUCT_STOCK_SCRIPT;

    static {
        DEDUCT_STOCK_SCRIPT = new DefaultRedisScript<>();
        //new ClassPathResource加载resource下的文件
        DEDUCT_STOCK_SCRIPT.setLocation(new ClassPathResource("deduct_stock.lua"));
        DEDUCT_STOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 下单购买
     *
     * @param query
     * @return
     */
    @Transactional
    @Override
    public Result<OrderVO> place(OrderPlaceQuery query) {
        // 校验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1.校验 productId 和 quantity（1-99）
        Long productId = query.getProductId();
        Integer quantity = query.getQuantity();

        if (productId == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), BizErrorCode.BAD_REQUEST.getMessage());
        }
        if (!(1 <= quantity && quantity <= 99)) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "购买数额异常");
        }
        // 2.查询 product 是否存在且 status=ON_SHELF，否则返回 400 "商品已下架"
        Product product = productService.lambdaQuery()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.ON_SHELF)
                .one();
        if (product == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), BizErrorCode.BAD_REQUEST.getMessage());
        }
        // 3.准备 Redis keys：shop:stock:{productId}，shop:limit:{userId}:{productId}:{date}，user:balance:{userId}
        // 3.1.准备key
        String stockKey = RedisKeyPrefix.SHOP_STOCK_KEY + productId;
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String limitKey = RedisKeyPrefix.SHOP_LIMIT_KEY + userId + ":" + productId + ":" + today;
        String balanceKey = RedisKeyPrefix.USER_BALANCE_KEY + userId;
        // 3.2.计算订单总金额（Lua 脚本需要从余额中扣除）
        BigDecimal price = product.getPrice();
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity));
        // 4.执行 Lua 脚本 deduct_stock.lua
        // 4.1.构造 KEYS 列表（注意：顺序必须与脚本中 KEYS[1]、KEYS[2]、KEYS[3] 对应）
        List<String> keys = Arrays.asList(stockKey, limitKey, balanceKey);
        // 4.2.构造 ARGV 数组（顺序对应 ARGV[1]、ARGV[2]、ARGV[3]）
        Object[] args = new Object[]{
                String.valueOf(quantity),      // ARGV[1] 购买数量
                product.getDailyLimit() == null ? null : String.valueOf(product.getDailyLimit()), // ARGV[2] 限购数，不限购传 null
                totalPrice.toString()          // ARGV[3] 订单总金额
        };
        // 4.3.执行并返回值
        Long result = stringRedisTemplate.execute(
                DEDUCT_STOCK_SCRIPT,
                keys,
                args
        );
        // 5.Lua 返回 -1：库存不足；-2：超过限购；-3：余额不足
        int r = result.intValue();
        if (r == -1) {
            return Result.fail(BizErrorCode.INSUFFICIENT_STOCK.getCode(), BizErrorCode.INSUFFICIENT_STOCK.getMessage());
        }
        if (r == -2) {
            return Result.fail(BizErrorCode.EXCEED_PURCHASE_LIMIT.getCode(), BizErrorCode.EXCEED_PURCHASE_LIMIT.getMessage());
        }
        if (r == -3) {
            // 余额不足
            return Result.fail(BizErrorCode.INSUFFICIENT_BALANCE.getCode(), BizErrorCode.INSUFFICIENT_BALANCE.getMessage());
        }
        // 6.Lua 成功返回后：生成 Snowflake 订单 ID，INSERT INTO shop_order（快照当前 price 作为 unitPrice）
        // TODO 限时秒杀！后台服务
        // 6.1.生成订单id
        long orderId = snowflakeIdGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();
        // 注意：订单中的 unitPrice 必须使用下单时的 product.price 快照（price 和 totalPrice 已在步骤 3 计算）
        // 6.2.构建插入对象
        ShopOrder shopOrder = ShopOrder.builder()
                .id(orderId)
                .userId(userId)
                .productId(productId)
                .productName(product.getName())
                .quantity(quantity)
                .unitPrice(price)
                .totalPrice(totalPrice)
                .status(OrderStatus.PAID)
                .createTime(now)
                .updateTime(now)
                .build();
        // 6.3.插入数据库
        save(shopOrder);
        // 6.4.更新 DB 钱包：扣减余额，累加累计支出
        userWalletService.lambdaUpdate()
                .eq(UserWallet::getUserId, userId)
                .setSql("balance = balance - " + totalPrice)
                .setSql("total_expense = total_expense + " + totalPrice)
                .set(UserWallet::getUpdateTime, now)
                .update();
        // 6.5.插入货币流水记录（EXPENSE / ORDER_PAY）
        // 从 Redis 读取 Lua 脚本扣减后的最新余额
        String newBalanceStr = stringRedisTemplate.opsForValue().get(balanceKey);
        BigDecimal balanceAfter = newBalanceStr != null ? new BigDecimal(newBalanceStr) : BigDecimal.ZERO;
        CurrencyTransaction transaction = new CurrencyTransaction();
        transaction.setId(snowflakeIdGenerator.nextId());
        transaction.setUserId(userId);
        transaction.setAmount(totalPrice.negate());  // 支出为负数
        transaction.setType(CurrencyTransactionType.EXPENSE);
        transaction.setBizType(CurrencyBizType.ORDER_PAY);
        transaction.setBizId(orderId);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setCreateTime(now);
        currencyTransactionService.save(transaction);
        // 6.6.写入用户背包：INSERT ... ON DUPLICATE KEY UPDATE 或手动 upsert
        UserBackpack existingBackpack = userBackpackService.lambdaQuery()
                .eq(UserBackpack::getUserId, userId)
                .eq(UserBackpack::getProductId, productId)
                .one();
        if (existingBackpack != null) {
            existingBackpack.setQuantity(existingBackpack.getQuantity() + quantity);
            existingBackpack.setTotalAcquired(existingBackpack.getTotalAcquired() + quantity);
            existingBackpack.setUpdateTime(now);
            userBackpackService.updateById(existingBackpack);
        } else {
            UserBackpack newBackpack = new UserBackpack();
            newBackpack.setId(snowflakeIdGenerator.nextId());
            newBackpack.setUserId(userId);
            newBackpack.setProductId(productId);
            newBackpack.setQuantity(quantity);
            newBackpack.setTotalAcquired(quantity);
            newBackpack.setTotalConsumed(0);
            newBackpack.setCreateTime(now);
            newBackpack.setUpdateTime(now);
            userBackpackService.save(newBackpack);
        }
        // 7.删除订单列表缓存：DEL shop:order:list:{userId}:*
        String orderListKey = RedisKeyPrefix.SHOP_ORDER_LIST_KEY + userId + ":*";
        Set<String> orderListKeys = stringRedisTemplate.keys(orderListKey);
        if (orderListKeys != null && !orderListKeys.isEmpty()) {
            stringRedisTemplate.delete(orderListKeys);
        }
        // 8.返回 OrderVO
        OrderVO orderVO = OrderVO.builder()
                .orderId(orderId)
                .productId(productId)
                .productName(product.getName())
                .quantity(quantity)
                .unitPrice(price)
                .totalPrice(totalPrice)
                .status(OrderStatus.PAID)
                .createTime(now)
                .build();
        return Result.ok(orderVO);
    }

    /**
     * 获取我的订单列表
     *
     * @param page
     * @param size
     * @return
     */
    @Override
    public Result<PageDTO<OrderListVO>> orderList(Integer page, Integer size) {
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.尝试读取 Redis 缓存：GET shop:order:list:{userId}:{page}:{size}
        // 2.1.转换类型 page,size
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo((page == null) ? 1 : page);
        pageQuery.setPageSize(size == null ? 10 : size);
        // 2.2.尝试从缓存中取出
        String pageKey = RedisKeyPrefix.SHOP_ORDER_LIST_KEY + userId + ":" + pageQuery.getPageNo() + ":" + pageQuery.getPageSize();
        String cachedJson = stringRedisTemplate.opsForValue().get(pageKey);

        // 2.3.若命中
        if (StringUtils.hasText(cachedJson)) {
            // 3.缓存命中且未过期（TTL 5 分钟）直接返回
            try {
                PageDTO<OrderListVO> cachedResult = objectMapper.readValue(cachedJson, new TypeReference<PageDTO<OrderListVO>>() {
                });
                return Result.ok(cachedResult);
            } catch (JsonProcessingException e) {
                // 如果 JSON 解析失败，就删掉脏缓存，继续走 DB 查询
                stringRedisTemplate.delete(pageKey);
            }
        }

        // 4.缓存未命中：SELECT * FROM shop_order WHERE user_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?
        // 4.1.构建分页条件
        Page<ShopOrder> sort = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        // 4.2.构建查询
        lambdaQuery()
                .eq(ShopOrder::getUserId, userId)
                .page(sort);
        // 5.查询总数、组装 PageDTO，写回 Redis 缓存：SETEX shop:order:list:{userId}:{page}:{size} 300 {json}
        // 5.1.查询总数、组装 PageDTO
        // 优化：加快查询效率，一次性查询所有的商品
        // 1. 收集本页所有商品的 ID（去重）
        Set<Long> productIds = sort.getRecords().stream()
                .map(ShopOrder::getProductId)
                .collect(Collectors.toSet());

        // 2. 一次性批量查询，构建 ID -> icon 的映射（空集合直接返回空 Map，避免生成 WHERE id IN () 非法 SQL）
        Map<Long, String> iconMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productService.listByIds(productIds)
                  .stream()
                  .collect(Collectors.toMap(Product::getId, Product::getIcon));

        // 3. 使用 PageDTO.of 完成转换，icon 直接从 Map 取，不再访问数据库
        PageDTO<OrderListVO> dto = PageDTO.of(sort, record -> {
            String icon = iconMap.get(record.getProductId());
            return OrderListVO.builder()
                    .orderId(record.getId())
                    .productId(record.getProductId())
                    .productName(record.getProductName())
                    .icon(icon)
                    .quantity(record.getQuantity())
                    .unitPrice(record.getUnitPrice())
                    .totalPrice(record.getTotalPrice())
                    .status(record.getStatus())
                    .createTime(record.getCreateTime())
                    .build();
        });
        // 5.2.写回 Redis 缓存
        //  5.2.1. 把对象转成 JSON 字符串（必须这一步）
        try {
            String jsonString = objectMapper.writeValueAsString(dto);
            // 5.2.2.存入redis
            // 空列表也缓存（防缓存穿透），但 TTL 可设短一些（如 60s）
            if (CollUtils.isEmpty(dto.getRecords())) {
                stringRedisTemplate.opsForValue().set(pageKey, jsonString, RedisKeyPrefix.SHOP_ORDER_LIST_NULL_TTL, TimeUnit.SECONDS);
            } else {
                stringRedisTemplate.opsForValue().set(pageKey, jsonString, RedisKeyPrefix.SHOP_ORDER_LIST_TTL, TimeUnit.SECONDS);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // 6.返回 PageDTO
        return Result.ok(dto);
    }

    /**
     * 订单详情
     *
     * @param orderId
     * @return
     */
    @Override
    public Result<OrderListVO> detailOrder(Long orderId) {
        // 所有权校验是核心安全要求：用户不能查看他人的订单详情
        // 1.从 UserContextHolder 获取当前 userId
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 2.查询 shop_order by id，不存在返回 404
        ShopOrder shopOrder = getById(orderId);
        if (shopOrder == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), BizErrorCode.NOT_FOUND.getMessage());
        }
        // 3.校验 order.user_id 与 UserContextHolder 中的当前 userId 一致，不匹配返回 403
        if (!shopOrder.getUserId().equals(userId)) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 4.直接返回 OrderVO（无需 Redis 缓存，低频调用）
        // 4.1.查询图标
        String icon = productService.getById(shopOrder.getProductId()).getIcon();
        OrderListVO build = OrderListVO.builder()
                .orderId(orderId)
                .productId(shopOrder.getProductId())
                .productName(shopOrder.getProductName())
                .icon(icon)
                .quantity(shopOrder.getQuantity())
                .unitPrice(shopOrder.getUnitPrice())
                .totalPrice(shopOrder.getTotalPrice())
                .status(shopOrder.getStatus())
                .createTime(shopOrder.getCreateTime())
                .build();
        return Result.ok(build);
    }
}
