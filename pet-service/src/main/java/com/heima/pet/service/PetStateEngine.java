package com.heima.pet.service;

import com.heima.api.enums.InteractionType;
import com.heima.api.enums.PetState;
import com.heima.pet.domain.vo.PetAttributeVO;

public class PetStateEngine {

    // 属性临界值
    public static final int CRITICAL_THRESHOLD = 20;
    public static final int RECOVERY_THRESHOLD = 50;
    // 衰减系数（每分钟）
    private static final int DECAY_HUNGER = 2;
    private static final int DECAY_MOOD = 3;
    private static final int DECAY_CLEANLINESS = 1;
    // 互动效果
    public static final int FEED_HUNGER = 15;
    public static final int FEED_MOOD = 5;
    public static final int PET_MOOD = 10;
    public static final int BATHE_CLEANLINESS = 20;
    public static final int BATHE_MOOD = 10;
    // 离家出走找回初始值
    public static final int RUNAWAY_RECOVER = 30;

    // 离家出走触发条件：处于 ANGRY/SICK 状态超过此时间（分钟）后，每次衰减有概率触发
    public static final long RUNAWAY_ANGRY_MINUTES = 30;
    public static final long RUNAWAY_SICK_MINUTES = 15;
    // 离家出走触发概率
    private static final double RUNAWAY_ANGRY_PROB = 0.05;   // 生气 30 分钟后，每次衰减 5%
    private static final double RUNAWAY_SICK_PROB = 0.08;    // 生病 15 分钟后，每次衰减 8%

    /**
     * 根据属性判断基础状态（不含 RUNAWAY）
     */
    public static PetState evaluateBaseState(int hunger, int mood, int cleanliness) {
        if (cleanliness < CRITICAL_THRESHOLD) {
            return PetState.SICK;
        } else if (mood < CRITICAL_THRESHOLD) {
            return PetState.ANGRY;
        } else if (hunger < CRITICAL_THRESHOLD) {
            return PetState.HUNGRY;
        } else {
            return PetState.NORMAL;
        }
    }

    /**
     * 根据属性判断新状态（含 RUNAWAY 保持）
     *
     * @return 新的 PetState
     */
    public static PetState evaluateState(int hunger, int mood, int cleanliness, PetState currentState) {
        if (currentState.equals(PetState.RUNAWAY)) {
            return PetState.RUNAWAY;
        }
        return evaluateBaseState(hunger, mood, cleanliness);
    }

    /**
     * 判断是否触发离家出走。
     * <p>
     * 触发条件：处于 ANGRY/SICK 状态超过阈值时间，每次衰减按概率随机出走。
     * 必须在 {@link #evaluateBaseState} 返回值就是 ANGRY/SICK 的前提下调用。
     *
     * @param currentState 当前状态
     * @param stateSinceMs 当前状态开始时间（毫秒时间戳），null 视为刚进入
     * @return true = 触发离家出走
     */
    public static boolean shouldRunaway(PetState currentState, Long stateSinceMs) {
        if (currentState == null) return false;
        if (stateSinceMs == null) return false;

        long elapsedMs = System.currentTimeMillis() - stateSinceMs;
        long elapsedMin = elapsedMs / 60_000;

        if (currentState == PetState.ANGRY && elapsedMin >= RUNAWAY_ANGRY_MINUTES) {
            return Math.random() < RUNAWAY_ANGRY_PROB;
        }
        if (currentState == PetState.SICK && elapsedMin >= RUNAWAY_SICK_MINUTES) {
            return Math.random() < RUNAWAY_SICK_PROB;
        }
        return false;
    }

    /**
     * 计算衰减后的属性。
     * 系数为整数，elapsedMinutes 为整数 → 无小数，直接 int 运算即可。
     *
     * @param hunger         当前饥饿值
     * @param mood           当前心情值
     * @param cleanliness    当前清洁值
     * @param elapsedMinutes 距上次衰减的分钟数
     * @return {newHunger, newMood, newCleanliness}
     */
    public static int[] applyDecay(int hunger, int mood, int cleanliness, long elapsedMinutes) {
        int newHunger = (int) Math.max(0, hunger - DECAY_HUNGER * elapsedMinutes / 5);
        int newMood = (int) Math.max(0, mood - DECAY_MOOD * elapsedMinutes / 5);
        int newCleanliness = (int) Math.max(0, cleanliness - DECAY_CLEANLINESS * elapsedMinutes / 5);
        return new int[]{newHunger, newMood, newCleanliness};
    }
}
