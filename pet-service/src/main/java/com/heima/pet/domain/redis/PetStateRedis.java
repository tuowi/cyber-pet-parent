package com.heima.pet.domain.redis;

import com.heima.api.enums.PetState;
import lombok.Data;

@Data
public class PetStateRedis {
    private Integer hunger;
    private Integer mood;
    private Integer cleanliness;
    private PetState state;
    /** 当前状态开始的时间戳（毫秒），用于判断离家出走触发条件 */
    private Long stateSince;
}
