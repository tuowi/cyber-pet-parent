package com.heima.analytics.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Breakdown {
    private Integer feedIntimacy;
    private Integer playIntimacy;
    private Integer bathIntimacy;
    private Integer penaltyIntimacy;
}
