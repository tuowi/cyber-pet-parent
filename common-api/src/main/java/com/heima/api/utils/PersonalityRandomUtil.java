package com.heima.api.utils;

import com.heima.api.enums.Personality;

import java.util.List;
import java.util.Random;

public class PersonalityRandomUtil {

    private static final Random RANDOM = new Random();

    public static Personality randomByWeight() {
        List<Personality> list = List.of(Personality.values());
        // 总权重100
        int totalWeight = 100;
        // 1~100随机数
        int randomNum = RANDOM.nextInt(totalWeight) + 1;

        int cur = 0;
        for (Personality c : list) {
            cur += c.getWeight();
            if (randomNum <= cur) {
                return c;
            }
        }
        // 兜底返回佛系
        return Personality.CLINGY;
    }
}
