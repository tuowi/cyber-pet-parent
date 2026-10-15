package com.heima.shop.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heima.api.constant.RedisKeyPrefix;
import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.BizErrorCode;
import com.heima.api.enums.InteractionType;
import com.heima.api.enums.OrderStatus;
import com.heima.api.enums.ProductStatus;
import com.heima.common.util.SnowflakeIdGenerator;
import com.heima.common.util.UserContextHolder;
import com.heima.shop.domain.po.Product;
import com.heima.shop.domain.po.ShopOrder;
import com.heima.shop.domain.query.*;
import com.heima.shop.domain.vo.EditProductVO;
import com.heima.shop.domain.vo.ProductDetailVO;
import com.heima.shop.mapper.ProductMapper;
import com.heima.shop.service.IProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.shop.service.IShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 道具商品表 服务实现类
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    private final StringRedisTemplate stringRedisTemplate;

    private final IShopOrderService shopOrderService;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ProductMapper productMapper;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * 商品列表
     *
     * @param query
     * @return
     */
    @Override
    public Result<PageDTO<Product>> productList(ProductPageQuery query) {
        // 检验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1.解析分页参数（page, size）和可选 keyword
        // 1.1.解析（先做默认值处理，避免 null 进入缓存 key）
        int pageNo = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getSize() != null ? query.getSize() : 10;
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNo(pageNo);
        pageQuery.setPageSize(pageSize);

        // keyword 变化频繁时可跳过缓存，只对无 keyword 的默认查询做缓存
        String pageKey = RedisKeyPrefix.SHOP_PRODUCT_LIST_KEY + pageNo + ":" + pageSize + ":" + query.getKeyword();

        // 1.2.尝试从缓存中取出
        String cachedJson = stringRedisTemplate.opsForValue().get(pageKey);

        if (StringUtils.hasText(cachedJson)) {
            // 缓存命中！直接反序列化返回，完全跳过下面的 DB 查询
            try {
                PageDTO<Product> cachedResult = objectMapper.readValue(cachedJson, new TypeReference<PageDTO<Product>>() {
                });
                return Result.ok(cachedResult);
            } catch (JsonProcessingException e) {
                // 如果 JSON 解析失败，就删掉脏缓存，继续走 DB 查询
                stringRedisTemplate.delete(pageKey);
            }
        }

        // 构建分页
        Page<Product> page = pageQuery.toMpPageDefaultSortByUpdateTimeDesc();
        // 2.构建查询：SELECT * FROM product WHERE status='ON_SHELF' AND deleted=0，有 keyword 时追加 AND name LIKE '%keyword%'
        lambdaQuery()
                .eq(Product::getStatus, ProductStatus.ON_SHELF)
                .like(query.getKeyword() != null, Product::getName, query.getKeyword())
                .page(page);
        // 3.遍历每个 product，从 Redis 读取实时库存：GET shop:stock:{productId}
        // 注意：列表缓存（60s TTL）需在库存变动时失效：4.3/4.7/4.8 中 DELETE shop:product:list:*
        List<Product> products = page.getRecords();
        // 若无商品
        if (CollUtil.isEmpty(products)) {
            return Result.ok(PageDTO.empty(page));
        }
        // 3.1.开始遍历
        for (Product product : products) {
            // 3.2.获取key
            String key = RedisKeyPrefix.SHOP_STOCK_KEY + product.getId();
            // 3.3.从 Redis 读取实时库存：GET shop:stock:{productId}
            String newStockStr = stringRedisTemplate.opsForValue().get(key);
            Integer newStock = null;
            if (StringUtils.hasText(newStockStr)) {
                newStock = Integer.parseInt(newStockStr);
                // 库存字段应从 Redis 实时读取（shop:stock:{productId}），不能返回 DB 中的旧库存值
                product.setStock(newStock);
            }
            if (newStock == null) {
                // 4.Redis 未命中时回退到 DB product.stock 字段，并回写 Redis
                newStock = product.getStock();
                stringRedisTemplate.opsForValue().set(key, String.valueOf(newStock)); //永久存储
            }
        }
        // 更新库存
        page.setRecords(products);
        // 类型转换
        PageDTO<Product> productPageDTO = PageDTO.of(page);
        // 5.缓存商品列表：SETEX shop:product:list:{page}:{size}:{keywordHash} 60 {json}
        //  5.1. 把对象转成 JSON 字符串（必须这一步）
        try {
            String jsonString = objectMapper.writeValueAsString(productPageDTO);
            // 5.2.存入redis
            stringRedisTemplate.opsForValue().set(pageKey, jsonString, RedisKeyPrefix.SHOP_PRODUCT_LIST_TTL, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // 6.返回 PageDTO
        return Result.ok(productPageDTO);
    }

    /**
     * 获取商品详情
     *
     * @param productId
     * @return
     */
    @Override
    public Result<ProductDetailVO> productDetail(Long productId) {
        // 检验用户是否登录
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), BizErrorCode.FORBIDDEN.getMessage());
        }
        // 1.查询 product by id WHERE deleted=0，不存在或已删除返回 404
        if (productId == null) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), BizErrorCode.BAD_REQUEST.getMessage());
        }
        Product product = lambdaQuery()
                .eq(Product::getId, productId)
                .eq(Product::getDeleted, 0)
                .one();
        if (product == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "该商品不存在或已经删除");
        }
        // 2.从 Redis 读取实时库存：GET shop:stock:{productId}，未命中回退 DB 并回写
        // 2.1.从redis中读取实时库存
        String stockKey = RedisKeyPrefix.SHOP_STOCK_KEY + productId;
        String newStockStr = stringRedisTemplate.opsForValue().get(stockKey);
        Integer newStock = null;
        if (StringUtils.hasText(newStockStr)) {
            newStock = Integer.parseInt(newStockStr);
            // 返回新库存
            product.setStock(newStock);

        }
        // 2.2.未命中回退 DB 并回写
        if (newStock == null) {
            newStock = product.getStock();
            stringRedisTemplate.opsForValue().set(stockKey, JSONUtil.toJsonStr(newStock)); //永久存储
        }
        // 3.从 Redis 读取今日已购数：GET shop:limit:{userId}:{productId}:{today}（key 格式 YYYY-MM-DD）
        // TODO 检测日期是否变形
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String limitKey = RedisKeyPrefix.SHOP_LIMIT_KEY + userId + ":" + productId + ":" + today;
        String todayBoughtStr = stringRedisTemplate.opsForValue().get(limitKey);
        Integer todayBought = null;
        if (StringUtils.hasText(todayBoughtStr)) {
            todayBought = Integer.parseInt(todayBoughtStr);
        }
        // 若未命中回退 DB 并回写
        if (todayBought == null) {
            // 3.1. 获取今天的起始时间 00:00:00
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            // 3.2. 获取明天的起始时间 00:00:00（作为结束边界）
            LocalDateTime startOfTomorrow = LocalDate.now().plusDays(1).atStartOfDay();

            List<ShopOrder> shopOrders = shopOrderService.lambdaQuery()
                    .eq(ShopOrder::getProductId, productId)
                    .eq(ShopOrder::getUserId, userId)
                    .eq(ShopOrder::getStatus, OrderStatus.PAID)
                    // 3.3. 拼接时间范围条件：create_time >= 今天 00:00:00 且 < 明天 00:00:00
                    .ge(ShopOrder::getCreateTime, startOfToday)
                    .lt(ShopOrder::getCreateTime, startOfTomorrow)
                    .list();

            todayBought = 0;
            for (ShopOrder shopOrder : shopOrders) {
                todayBought += shopOrder.getQuantity();
            }

            // 3.4.写回redis
            LocalDateTime now = LocalDateTime.now();
            // 计算剩余秒数（到明天凌晨的时长）
            long secondsUntilMidnight = ChronoUnit.SECONDS.between(now, startOfTomorrow);
            stringRedisTemplate.opsForValue().set(limitKey, String.valueOf(todayBought), secondsUntilMidnight, TimeUnit.SECONDS);
        }
        // 4.计算今日剩余：若 dailyLimit 为 null，todayRemaining 也为 null（不限）；否则 todayRemaining = dailyLimit - todayBought
        Integer todayRemaining = null;
        if (product.getDailyLimit() == null) {
            todayRemaining = null;
        } else {
            todayRemaining = product.getDailyLimit() - todayBought;
        }
        // 5.返回 ProductDetailVO
        ProductDetailVO productDetailVO = new ProductDetailVO();
        BeanUtil.copyProperties(product, productDetailVO);
        productDetailVO.setTodayBought(todayBought);
        productDetailVO.setTodayRemaining(todayRemaining);
        return Result.ok(productDetailVO);
    }

    // ==================== 管理端接口 ====================

    /**
     * 新增商品（管理端）
     */
    @Override
    public Result<Long> createProduct(ProductCreateQuery query) {
        // 1.校验管理员权限
        if (!UserContextHolder.isAdmin()) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "无管理员权限");
        }
        // 2.校验字段：dailyLimit 若 <= 0 则视为 null；effect 字段默认 0（null → 0）
        // name（1-50 字符）、price（> 0）  请求体已经校验
        // dailyLimit
        Integer dailyLimit = query.getDailyLimit();
        if (dailyLimit != null && dailyLimit <= 0) {
            dailyLimit = null;
        }
        Integer effectHunger = query.getEffectHunger() != null ? query.getEffectHunger() : 0;
        Integer effectMood = query.getEffectMood() != null ? query.getEffectMood() : 0;
        Integer effectCleanliness = query.getEffectCleanliness() != null ? query.getEffectCleanliness() : 0;
        // 3.生成 Snowflake ID，INSERT INTO product
        long productId = snowflakeIdGenerator.nextId();
        Product product = new Product();
        product.setId(productId);
        product.setName(query.getName());
        product.setDescription(query.getDescription());
        product.setIcon(query.getIcon());
        product.setPrice(query.getPrice());
        product.setStock(query.getStock());
        product.setDailyLimit(dailyLimit);
        product.setEffectType(query.getEffectType());
        product.setEffectHunger(effectHunger);
        product.setEffectMood(effectMood);
        product.setEffectCleanliness(effectCleanliness);
        product.setStatus(ProductStatus.ON_SHELF);
        product.setCreateTime(java.time.LocalDateTime.now());
        product.setUpdateTime(java.time.LocalDateTime.now());
        product.setDeleted(0);
        save(product);
        // 4.同步 Redis 初始库存
        String stockKey = RedisKeyPrefix.SHOP_STOCK_KEY + productId;
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(query.getStock()));
        // 5.清空商品列表缓存
        clearProductListCache();
        // 6.返回 productId
        return Result.ok(productId);
    }

    /**
     * 上下架商品（管理端）
     */
    @Override
    public Result<Void> updateProductStatus(Long productId, ProductStatus status) {
        // 1.校验管理员权限
        if (!UserContextHolder.isAdmin()) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "无管理员权限");
        }
        // 2.查询 product by id，不存在或 deleted=1 返回 404
        Product product = getById(productId);
        if (product == null || product.getDeleted() == 1) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "商品不存在");
        }
        // 3.若与当前状态相同返回 400 "状态未变化"
        ProductStatus newStatus = ProductStatus.valueOf(status.name());
        if (newStatus == product.getStatus()) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "状态未变化");
        }
        // 4.UPDATE product SET status=? WHERE id=?
        lambdaUpdate()
                .eq(Product::getId, productId)
                .set(Product::getStatus, newStatus)
                .set(Product::getUpdateTime, java.time.LocalDateTime.now())
                .update();
        // 5.清空商品列表缓存
        clearProductListCache();
        // 6.返回
        return Result.ok();
    }

    /**
     * 修改商品库存（管理端）
     */
    @Override
    public Result<Void> updateProductStock(Long productId, ProductStockQuery query) {
        // 1.校验管理员权限
        if (!UserContextHolder.isAdmin()) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "无管理员权限");
        }
        // 2.查询 product by id，不存在返回 404
        Product product = getById(productId);
        if (product == null || product.getDeleted() == 1) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "商品不存在");
        }
        // 3.更新 DB
        lambdaUpdate()
                .eq(Product::getId, productId)
                .set(Product::getStock, query.getStock())
                .set(Product::getUpdateTime, java.time.LocalDateTime.now())
                .update();
        // 4.同步 Redis
        String stockKey = RedisKeyPrefix.SHOP_STOCK_KEY + productId;
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(query.getStock()));
        // 5.清空商品列表缓存
        clearProductListCache();
        // 6.返回
        return Result.ok();
    }

    /**
     * 清除商品列表缓存（pattern delete）
     */
    private void clearProductListCache() {
        String pattern = RedisKeyPrefix.SHOP_PRODUCT_LIST_KEY + "*";
        var keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /**
     * 根据商品名称进行模糊搜索，搜索到最匹配的商品并返回
     *
     * @param productName
     * @return
     */
    @Override
    public Product getProductLikeProductName(String productName, InteractionType type) {
        if (productName == null || productName.isBlank()) {
            return null;
        }

        // 先查询所有匹配的商品（不作自定义排序）
        List<Product> products = lambdaQuery()
                .like(Product::getName, productName)
                .eq(Product::getEffectType, type)
                .eq(Product::getStatus, ProductStatus.ON_SHELF)
                .list();

        if (products == null || products.isEmpty()) {
            return null;
        }

        // 在 Java 中按相关度排序：精确匹配 > 前缀匹配 > 出现次数 > 出现位置
        products.sort((a, b) -> {
            String nameA = a.getName();
            String nameB = b.getName();
            // 1. 完全匹配优先
            boolean exactA = nameA.equals(productName);
            boolean exactB = nameB.equals(productName);
            if (exactA != exactB) return exactA ? -1 : 1;
            // 2. 前缀匹配次之
            boolean prefixA = nameA.startsWith(productName);
            boolean prefixB = nameB.startsWith(productName);
            if (prefixA != prefixB) return prefixA ? -1 : 1;
            // 3. 长度短的更匹配（更接近关键词）
            return Integer.compare(nameA.length(), nameB.length());
        });

        return products.get(0);
    }

    /**
     * 编辑商品（管理端）
     *
     * @param productId
     * @param editProductQuery
     * @return
     */
    @Override
    public Result<EditProductVO> editProduct(Long productId, EditProductQuery editProductQuery) {
        // 1.校验管理员权限
        if (!UserContextHolder.isAdmin()) {
            return Result.fail(BizErrorCode.FORBIDDEN.getCode(), "无管理员权限");
        }
        // 2.查询 product by id，若为 null 或 deleted=1 返回 404
        Product product = getById(productId);
        if (product == null) {
            return Result.fail(BizErrorCode.NOT_FOUND.getCode(), "商品不存在");
        }
        // 3.校验字段：name（1-50 字符）、price（> 0），dailyLimit 若 <= 0 则视为 null
        if (editProductQuery.getName().isBlank() || editProductQuery.getName().length() > 50) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "商品名词不符合");
        }
        if (editProductQuery.getPrice() == null || editProductQuery.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "价格不符合");
        }
        if (editProductQuery.getDailyLimit() != null && editProductQuery.getDailyLimit() <= 0) {
            editProductQuery.setDailyLimit(null);
        }
        // 4.校验 effectType 是否为 FEED / PET / BATHE 之一，否则返回 400
        // 4.1.创建自定义注解 @AllowedInteractionType
        // 4.2.创建校验器
        // 4.3.在 DTO 中使用
        // 5.effectHunger / effectMood / effectCleanliness 未传时默认 0
        editProductQuery.setEffectHunger(editProductQuery.getEffectHunger() == null ? 0 : editProductQuery.getEffectHunger());
        editProductQuery.setEffectMood(editProductQuery.getEffectMood() == null ? 0 : editProductQuery.getEffectMood());
        editProductQuery.setEffectCleanliness(editProductQuery.getEffectCleanliness() == null ? 0 : editProductQuery.getEffectCleanliness());
        // 6.UPDATE product SET name=?, description=?, icon=?, price=?, daily_limit=?, effect_type=?, effect_hunger=?, effect_mood=?, effect_cleanliness=?, update_time=NOW() WHERE id=?
        BeanUtil.copyProperties(editProductQuery, product);
        LocalDateTime now = LocalDateTime.now();
        product.setUpdateTime(now);
        boolean isSuccess = updateById(product);
        if (!isSuccess) {
            return Result.fail(BizErrorCode.BAD_REQUEST.getCode(), "更新失败");
        }
        // 7.清空商品列表缓存：DEL shop:product:list:*
        clearProductListCache();
        // 8.返回更新后的关键字段（productId, name, price, dailyLimit, effectType, updateTime）
        EditProductVO build = EditProductVO.builder()
                .productId(productId)
                .name(editProductQuery.getName())
                .price(editProductQuery.getPrice())
                .dailyLimit(editProductQuery.getDailyLimit())
                .effectType(editProductQuery.getEffectType())
                .updateTime(now)
                .build();
        return Result.ok(build);
    }
}
