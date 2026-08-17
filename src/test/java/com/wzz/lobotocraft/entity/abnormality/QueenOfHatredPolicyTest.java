package com.wzz.lobotocraft.entity.abnormality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueenOfHatredPolicyTest {
    @Test
    void stageRequiresThreeDeathsToProtectTheCounter() {
        assertTrue(QueenOfHatredPolicy.shouldReduceCounterAtStageChange(0));
        assertTrue(QueenOfHatredPolicy.shouldReduceCounterAtStageChange(2));
        assertFalse(QueenOfHatredPolicy.shouldReduceCounterAtStageChange(3));
    }

    @Test
    void heroHysteriaStartsAtTwentyPercent() {
        assertFalse(QueenOfHatredPolicy.shouldBecomeHysteric(1, 6));
        assertTrue(QueenOfHatredPolicy.shouldBecomeHysteric(1, 5));
        assertTrue(QueenOfHatredPolicy.shouldBecomeHysteric(2, 9));
    }

    @Test
    void hystericWorkUsesExactPeBoundaryAndPercentagePointPenalty() {
        assertFalse(QueenOfHatredPolicy.overcomesHysteria(15));
        assertTrue(QueenOfHatredPolicy.overcomesHysteria(16));
        assertEquals(0.35F, QueenOfHatredPolicy.applyHystericWorkPenalty(0.55F), 0.0001F);
        assertEquals(0.0F, QueenOfHatredPolicy.applyHystericWorkPenalty(0.10F), 0.0001F);
    }
}
