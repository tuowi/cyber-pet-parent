package com.heima.shop.service;

import com.heima.api.domain.dto.PageDTO;
import com.heima.api.domain.dto.Result;
import com.heima.api.enums.InteractionType;
import com.heima.api.enums.ProductStatus;
import com.heima.shop.domain.po.Product;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.shop.domain.query.EditProductQuery;
import com.heima.shop.domain.query.ProductCreateQuery;
import com.heima.shop.domain.query.ProductPageQuery;
import com.heima.shop.domain.query.ProductStockQuery;
import com.heima.shop.domain.vo.EditProductVO;
import com.heima.shop.domain.vo.ProductDetailVO;

/**
 * <p>
 * 道具商品表 服务类
 * </p>
 *
 * @author lucy
 * @since 2026-07-16
 */
public interface IProductService extends IService<Product> {

    /**
     * 商品列表
     * @param query
     * @return
     */
    Result<PageDTO<Product>> productList(ProductPageQuery query);

    /**
     * 商品详情
     * @param productId
     * @return
     */
    Result<ProductDetailVO> productDetail(Long productId);

    /**
     * 新增商品（管理端）
     * @param query
     * @return
     */
    Result<Long> createProduct(ProductCreateQuery query);

    /**
     * 上下架商品（管理端）
     * @param productId
     * @param status
     * @return
     */
    Result<Void> updateProductStatus(Long productId, ProductStatus status);

    /**
     * 修改商品库存（管理端）
     * @param productId
     * @param query
     * @return
     */
    Result<Void> updateProductStock(Long productId, ProductStockQuery query);

    // 根据商品名称进行模糊搜索，搜索到最匹配的商品并返回
    Product getProductLikeProductName(String productName, InteractionType type);

    // 编辑商品（管理端）
    Result<EditProductVO> editProduct(Long productId, EditProductQuery editProductQuery);
}
