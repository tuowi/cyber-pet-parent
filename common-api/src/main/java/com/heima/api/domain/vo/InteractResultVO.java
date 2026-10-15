package com.heima.api.domain.vo;

import com.heima.api.enums.InteractionType;
import com.heima.api.enums.PetState;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InteractResultVO {
    private InteractionType type;
    private String typeName;
    private PetState beforeState;
    private PetState afterState;
    private boolean stateChanged;
    private PetAttributeVO attributes;
    private PetEffectVO effects;
    private String cuteMessage;
}
