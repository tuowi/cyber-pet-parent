package com.heima.pet.domain.vo;

import com.heima.pet.domain.po.StateTransitionLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "状态变更记录视图")
public class StateTransitionVO extends StateTransitionLog {
    @Schema(description = "原状态")
    private String fromStateName;
    @Schema(description = "新状态")
    private String toStateName;
    @Schema(description = "触发方式")
    private String triggerTypeName;
}
