package com.heima.api.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heima.api.enums.Personality;
import com.heima.api.enums.PetState;
import com.heima.api.enums.Species;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class PetVO {
    private Long petId;
    private String petName;
    private Species species;
    private String speciesName;
    private String personality;
    private PetState state;
    private String stateName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime runawayTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private PetAttributeVO attributes;
}
