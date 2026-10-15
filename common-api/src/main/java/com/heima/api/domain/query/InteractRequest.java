package com.heima.api.domain.query;

import com.heima.api.enums.InteractionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.util.annotation.Nullable;

/**
 * 宠物互动请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InteractRequest {

    private InteractionType type;

    @Nullable
    private Long productId;
}
