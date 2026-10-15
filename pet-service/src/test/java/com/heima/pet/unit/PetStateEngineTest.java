package com.heima.pet.unit;

import com.heima.api.enums.PetState;
import com.heima.pet.service.PetStateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("宠物状态机引擎 PetStateEngine")
class PetStateEngineTest {

    @Nested
    @DisplayName("evaluateBaseState - 基础状态判断")
    class EvaluateBaseState {

        @Test
        @DisplayName("全部正常 → NORMAL")
        void allNormal() {
            assertEquals(PetState.NORMAL,
                PetStateEngine.evaluateBaseState(80, 80, 80));
        }

        @Test
        @DisplayName("清洁值低于 20 → SICK")
        void cleanlinessLowReturnsSick() {
            assertEquals(PetState.SICK,
                PetStateEngine.evaluateBaseState(80, 80, 10));
        }

        @Test
        @DisplayName("心情低于 20 → ANGRY")
        void moodLowReturnsAngry() {
            assertEquals(PetState.ANGRY,
                PetStateEngine.evaluateBaseState(80, 10, 80));
        }

        @Test
        @DisplayName("饥饿值低于 20 → HUNGRY")
        void hungerLowReturnsHungry() {
            assertEquals(PetState.HUNGRY,
                PetStateEngine.evaluateBaseState(10, 80, 80));
        }

        @Test
        @DisplayName("优先级：SICK > ANGRY > HUNGRY（同时低于临界值）")
        void prioritySickOverAngryOverHungry() {
            assertEquals(PetState.SICK,
                PetStateEngine.evaluateBaseState(10, 10, 10));
        }

        @Test
        @DisplayName("边界值：刚好等于 20 → NORMAL（源码用 < 判断）")
        void boundaryExactlyAt20ReturnsNormal() {
            assertEquals(PetState.NORMAL,
                PetStateEngine.evaluateBaseState(20, 80, 80));
        }

        @Test
        @DisplayName("边界值：19 → HUNGRY（刚好低于临界值）")
        void boundaryJustBelowThreshold() {
            assertEquals(PetState.HUNGRY,
                PetStateEngine.evaluateBaseState(19, 80, 80));
        }
    }

    @Nested
    @DisplayName("applyDecay - 属性衰减")
    class ApplyDecay {

        @ParameterizedTest(name = "衰减 {3} 分钟: hunger {0}→{4}, mood {1}→{5}, cleanliness {2}→{6}")
        @CsvSource({
            "100, 100, 100, 30,  88,  82,  94",
            "10,  100, 100, 30,   0,  82,  94",
            "100, 100, 100,  0, 100, 100, 100",
        })
        @DisplayName("衰减计算正确，不低于 0")
        void decayClamped(int hunger, int mood, int clean, long elapsed,
                          int expH, int expM, int expC) {
            int[] result = PetStateEngine.applyDecay(hunger, mood, clean, elapsed);
            assertAll(
                () -> assertEquals(expH, result[0], "hunger"),
                () -> assertEquals(expM, result[1], "mood"),
                () -> assertEquals(expC, result[2], "cleanliness")
            );
        }

        @Test
        @DisplayName("长时间衰减不会低于 0（下限保护）")
        void decayNeverBelowZero() {
            int[] result = PetStateEngine.applyDecay(1, 0, 0, 1000);
            assertAll(
                () -> assertEquals(0, result[0]),
                () -> assertEquals(0, result[1]),
                () -> assertEquals(0, result[2])
            );
        }
    }

    @Nested
    @DisplayName("evaluateState - 含 RUNAWAY 保持")
    class EvaluateState {

        @Test
        @DisplayName("RUNAWAY 状态保持不降级")
        void runawayStateIsSticky() {
            assertEquals(PetState.RUNAWAY,
                PetStateEngine.evaluateState(100, 100, 100, PetState.RUNAWAY));
        }

        @Test
        @DisplayName("非 RUNAWAY 状态根据属性评估")
        void normalStateEvaluatesByAttr() {
            assertEquals(PetState.HUNGRY,
                PetStateEngine.evaluateState(10, 80, 80, PetState.NORMAL));
        }
    }

    @Nested
    @DisplayName("shouldRunaway - 离家出走触发")
    class ShouldRunaway {

        @Test
        @DisplayName("currentState 为 null → false")
        void nullStateReturnsFalse() {
            assertFalse(PetStateEngine.shouldRunaway(null, 1000L));
        }

        @Test
        @DisplayName("stateSinceMs 为 null → false")
        void nullStateSinceReturnsFalse() {
            assertFalse(PetStateEngine.shouldRunaway(PetState.ANGRY, null));
        }

        @Test
        @DisplayName("ANGRY 超过 30 分钟 → 有概率触发（5%，1000 次采样）")
        void angryOver30minHasProbability() {
            long thirtyOneMinutesAgo = System.currentTimeMillis() - 31 * 60_000L;
            int triggerCount = 0;
            for (int i = 0; i < 1000; i++) {
                if (PetStateEngine.shouldRunaway(PetState.ANGRY, thirtyOneMinutesAgo)) {
                    triggerCount++;
                }
            }
            assertTrue(triggerCount > 0,
                "概率 5%，1000 次采样至少触发 1 次，实际: " + triggerCount);
        }

        @Test
        @DisplayName("ANGRY 不足 30 分钟 → 不触发")
        void angryUnder30minNeverTriggers() {
            long tenMinutesAgo = System.currentTimeMillis() - 10 * 60_000L;
            for (int i = 0; i < 100; i++) {
                assertFalse(PetStateEngine.shouldRunaway(PetState.ANGRY, tenMinutesAgo));
            }
        }

        @Test
        @DisplayName("SICK 超过 15 分钟 → 有概率触发（8%，1000 次采样）")
        void sickOver15minHasProbability() {
            long sixteenMinutesAgo = System.currentTimeMillis() - 16 * 60_000L;
            int triggerCount = 0;
            for (int i = 0; i < 1000; i++) {
                if (PetStateEngine.shouldRunaway(PetState.SICK, sixteenMinutesAgo)) {
                    triggerCount++;
                }
            }
            assertTrue(triggerCount > 0,
                "概率 8%，1000 次采样至少触发 1 次，实际: " + triggerCount);
        }

        @Test
        @DisplayName("NORMAL 状态不管多久都不触发离家出走")
        void normalNeverTriggers() {
            long longTimeAgo = System.currentTimeMillis() - 60 * 60_000L;
            for (int i = 0; i < 100; i++) {
                assertFalse(PetStateEngine.shouldRunaway(PetState.NORMAL, longTimeAgo));
            }
        }
    }
}
