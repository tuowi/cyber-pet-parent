package com.heima.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum Personality {
    CLINGY("粘人", 40),
    TSUNDERE("傲娇", 25),
    GLUTTON("贪吃", 25),
    BUDDHIST("佛系", 10);

    @EnumValue
    private final String name;
    private final int weight;

    Personality(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }

    public static Personality nextString(String name){
        if (name.equals(Personality.CLINGY.getName())){
            return Personality.CLINGY;
        }else if (name.equals(Personality.TSUNDERE.getName())){
            return Personality.TSUNDERE;
        } else if (name.equals(Personality.GLUTTON.getName())) {
            return Personality.GLUTTON;
        }else if (name.equals(Personality.BUDDHIST.getName())){
            return Personality.BUDDHIST;
        }else {
            return null;
        }
    }
}
