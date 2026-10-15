package com.heima.shop.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "商品分页查询请求")
public class ProductPageQuery {

    @Schema(description = "页码", example = "1")
    private Integer page;        // 默认值 1

    @Schema(description = "每页条数", example = "10")
    private Integer size;       // 默认值 10

    @Schema(description = "搜索关键词（匹配商品名）")
    private String keyword;          // 非必填，默认为 null
}
