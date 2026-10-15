package com.heima.analytics.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "宠物亲密度详细返回")
public class IntimacyDetailVO {
    private Long petId;
    private String petName;
    private Integer intimacy;
    private Breakdown breakdown;
    private List<RecentEvents> recentEvents;
}
