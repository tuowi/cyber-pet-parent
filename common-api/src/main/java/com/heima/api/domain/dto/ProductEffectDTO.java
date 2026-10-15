package com.heima.api.domain.dto;

import com.heima.api.enums.InteractionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 道具消耗返回 — 由 shop-service 返回给 pet-service 的效果值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEffectDTO {

    /** 道具分类 */
    private InteractionType effectType;

    /** 饥饿值效果 */
    private Integer effectHunger;

    /** 心情值效果 */
    private Integer effectMood;

    /** 清洁值效果 */
    private Integer effectCleanliness;

    /** 道具名称 */
    private String productName;
}
