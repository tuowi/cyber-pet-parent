package com.heima.shop.controller;


import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.InteractionType;
import com.heima.api.enums.ProductStatus;
import com.heima.shop.domain.po.Product;
import com.heima.shop.domain.query.EditProductQuery;
import com.heima.shop.domain.query.ProductCreateQuery;
import com.heima.shop.domain.query.ProductPageQuery;
import com.heima.shop.domain.query.ProductStockQuery;
import com.heima.shop.domain.vo.EditProductVO;
import com.heima.shop.domain.vo.ProductDetailVO;
import com.heima.shop.service.IProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 道具商品表 前端控制器
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
@Slf4j
@Tag(name = "道具商品接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/shop/product")
public class ProductController {

    private final IProductService productService;

    // ==================== 用户端接口 ====================

    @Operation(summary = "商品列表")
    @PostMapping("/list")
    public Result<PageDTO<Product>> list(@RequestBody ProductPageQuery query) {
        log.info("商品列表，页码：{}，页大小：{}，关键字：{}", query.getPage(), query.getSize(), query.getKeyword());
        return productService.productList(query);
    }

    @Operation(summary = "商品详情")
    @GetMapping("/{productId}")
    public Result<ProductDetailVO> getProductDetail(@PathVariable Long productId) {
        log.info("获取商品详情：{}", productId);
        return productService.productDetail(productId);
    }

    // ==================== 管理端接口 ====================

    @Operation(summary = "新增商品（管理端）")
    @PostMapping
    public Result<Long> createProduct(@Validated @RequestBody ProductCreateQuery query) {
        log.info("管理端-新增商品：{}", query.getName());
        return productService.createProduct(query);
    }

    @Operation(summary = "上下架商品（管理端）")
    @PutMapping("/{productId}/status")
    public Result<Void> updateProductStatus(@PathVariable Long productId,
                                            @RequestParam ProductStatus status) {
        log.info("管理端-上下架商品：productId={}, status={}", productId, status);
        return productService.updateProductStatus(productId, status);
    }

    @Operation(summary = "修改商品库存（管理端）")
    @PutMapping("/{productId}/stock")
    public Result<Void> updateProductStock(@PathVariable Long productId,
                                           @Validated @RequestBody ProductStockQuery query) {
        log.info("管理端-修改库存：productId={}, stock={}", productId, query.getStock());
        return productService.updateProductStock(productId, query);
    }

    @Operation(summary = "编辑商品信息（管理端）")
    @PutMapping("/{productId}")
    public Result<EditProductVO> editProduct(@PathVariable Long productId,
                                             @Valid @RequestBody EditProductQuery editProductQuery) {
        log.info("编辑商品信息（管理端）,商品id:{}，更改信息：{}", productId, editProductQuery);
        return productService.editProduct(productId, editProductQuery);
    }


    // 内部接口-根据商品名称进行模糊搜索，搜索到最匹配的商品并返回
    @GetMapping("/like/productName")
    Product getProductLikeProductName(@RequestParam("productName") String productName,
                                      @RequestParam("type") InteractionType type) {
        log.info("根据商品名称进行模糊搜索，搜索到最匹配的商品并返回：{},商品种类：{}", productName, type);
        return productService.getProductLikeProductName(productName, type);
    }

    // 内部接口-根据商品id列表返回商品列表
    @GetMapping("/list/ids")
    List<Product> getProductListByIds(@RequestParam List<Long> ids) {
        log.info("根据商品id列表返回商品列表:{}", ids);
        return productService.listByIds(ids);
    }
}
